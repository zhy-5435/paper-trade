package com.stocksim.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.TradeCalendar;
import com.stocksim.common.cache.CacheKeys;
import com.stocksim.common.cache.RedisCacheClient;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.impl.OrderCancelService;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.entity.UserPosition;
import com.stocksim.position.mapper.UserPositionMapper;
import com.stocksim.settlement.dto.SettlementResult;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.entity.StockQuote;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import com.stocksim.stock.util.PriceLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * 第一轮最小日切（R1规划§6.8，幂等可重跑 P6）：过期撤单解冻 → 行情日切 → T+1 清零。
 * 本轮唯一实现类，第四轮原地扩展七步（不搬家、不写 settlement_batch）。
 * R2：过期撤单改走 OrderCancelService 撤单核心（Q3 在途检查 + Q12 trade_date<= 扫描）。
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final int MAX_CAS_RETRY = 3;

    private final TradeOrderMapper tradeOrderMapper;
    private final StockQuoteMapper stockQuoteMapper;
    private final StockInfoMapper stockInfoMapper;
    private final UserPositionMapper userPositionMapper;
    private final RedisCacheClient cacheClient;
    private final LockProvider lockProvider;
    private final OrderCancelService orderCancelService;

    public SettlementService(TradeOrderMapper tradeOrderMapper, StockQuoteMapper stockQuoteMapper,
                             StockInfoMapper stockInfoMapper, UserPositionMapper userPositionMapper,
                             RedisCacheClient cacheClient, LockProvider lockProvider,
                             OrderCancelService orderCancelService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.stockQuoteMapper = stockQuoteMapper;
        this.stockInfoMapper = stockInfoMapper;
        this.userPositionMapper = userPositionMapper;
        this.cacheClient = cacheClient;
        this.lockProvider = lockProvider;
        this.orderCancelService = orderCancelService;
    }

    public SettlementResult run() {
        Lock leader = lockProvider.settlementLeaderLock();
        boolean acquired;
        try {
            acquired = leader.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SYSTEM_BUSY, "清算执行中");
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "清算执行中");
        }
        if (!acquired) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "清算执行中");
        }
        try {
            LocalDate closeDate = currentQuoteTradeDate();
            if (closeDate == null) {
                log.info("无行情，跳过日切");
                return new SettlementResult(0, 0, 0, null, null);
            }
            LocalDate nextDate = TradeCalendar.nextTradingDay(closeDate);
            int expired = expireOrders(closeDate);
            int cutQuotes = cutQuotes(closeDate, nextDate);
            int cleared = clearTodayBuy();
            cacheClient.deleteByPattern(CacheKeys.QUOTE_PATTERN);
            log.info("最小日切完成: closeDate={}, nextDate={}, 过期撤单={}, 行情日切={}, T+1清零={}",
                    closeDate, nextDate, expired, cutQuotes, cleared);
            return new SettlementResult(expired, cutQuotes, cleared, closeDate, nextDate);
        } finally {
            leader.unlock();
        }
    }

    private LocalDate currentQuoteTradeDate() {
        StockQuote quote = stockQuoteMapper.selectOne(new LambdaQueryWrapper<StockQuote>().last("LIMIT 1"));
        return quote == null ? null : quote.getTradeDate();
    }

    /** 步骤1：扫 trade_date<=收盘日 的未成交完订单（Q12 防日切后缓存旧日期下单永不过期），逐单走撤单核心。 */
    private int expireOrders(LocalDate closeDate) {
        List<TradeOrder> openOrders = tradeOrderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .le(TradeOrder::getTradeDate, closeDate)
                        .in(TradeOrder::getStatus, OrderStateMachine.PENDING, OrderStateMachine.PARTIAL_FILLED)
                        .orderByAsc(TradeOrder::getStockCode, TradeOrder::getId));
        int expired = 0;
        for (TradeOrder order : openOrders) {
            if (orderCancelService.expireOne(order.getOrderNo(), order.getStockCode())) {
                expired++;
            }
        }
        return expired;
    }

    /** 步骤2：逐股 WHERE trade_date=收盘日 日切，version CAS，已日切不再命中（幂等） */
    private int cutQuotes(LocalDate closeDate, LocalDate nextDate) {
        Map<String, BigDecimal> ratios = stockInfoMapper.selectList(null).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, StockInfo::getPriceLimitRatio));
        List<StockQuote> quotes = stockQuoteMapper.selectList(
                new LambdaQueryWrapper<StockQuote>().eq(StockQuote::getTradeDate, closeDate));
        int cut = 0;
        for (StockQuote quote : quotes) {
            if (cutOneQuote(quote.getStockCode(), closeDate, nextDate, ratios.get(quote.getStockCode()))) {
                cut++;
            }
        }
        return cut;
    }

    private boolean cutOneQuote(String stockCode, LocalDate closeDate, LocalDate nextDate, BigDecimal ratio) {
        for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
            StockQuote current = stockQuoteMapper.selectOne(
                    new LambdaQueryWrapper<StockQuote>().eq(StockQuote::getStockCode, stockCode));
            if (current == null || !closeDate.equals(current.getTradeDate())) {
                return false;
            }
            BigDecimal newPreClose = current.getLatestPrice();
            int rows = stockQuoteMapper.update(null, new LambdaUpdateWrapper<StockQuote>()
                    .eq(StockQuote::getId, current.getId())
                    .eq(StockQuote::getTradeDate, closeDate)
                    .eq(StockQuote::getVersion, current.getVersion())
                    .set(StockQuote::getPreClosePrice, newPreClose)
                    .set(StockQuote::getLimitUpPrice, PriceLimits.up(newPreClose, ratio))
                    .set(StockQuote::getLimitDownPrice, PriceLimits.down(newPreClose, ratio))
                    .set(StockQuote::getOpenPrice, null)
                    .set(StockQuote::getHighPrice, null)
                    .set(StockQuote::getLowPrice, null)
                    .set(StockQuote::getTotalVolume, 0L)
                    .set(StockQuote::getTotalAmount, BigDecimal.ZERO)
                    .set(StockQuote::getChangeRatio, BigDecimal.ZERO)
                    .set(StockQuote::getTradeDate, nextDate)
                    .set(StockQuote::getVersion, current.getVersion() + 1)
                    .set(StockQuote::getQuoteTime, LocalDateTime.now(TradeCalendar.ZONE)));
            if (rows > 0) {
                return true;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_BUSY, "行情日切并发冲突: " + stockCode);
    }

    /** 步骤3：T+1 当日买入清零（不落流水，03§四.7 口径），幂等 */
    private int clearTodayBuy() {
        return userPositionMapper.update(null, new LambdaUpdateWrapper<UserPosition>()
                .gt(UserPosition::getTodayBuyQty, 0)
                .set(UserPosition::getTodayBuyQty, 0));
    }
}