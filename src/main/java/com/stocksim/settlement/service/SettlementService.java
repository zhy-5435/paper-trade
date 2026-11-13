package com.stocksim.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.TradeCalendar;
import com.stocksim.match.engine.MatchEngine;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.entity.UserPosition;
import com.stocksim.position.mapper.UserPositionMapper;
import com.stocksim.position.service.PositionService;
import com.stocksim.settlement.dto.SettlementResult;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.entity.StockQuote;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import com.stocksim.stock.util.PriceLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 第一轮最小日切（07§6.8，幂等可重跑 P6）：过期撤单解冻 → 行情日切 → T+1 清零。
 * 本轮唯一实现类，第四轮原地扩展七步（不搬家、不写 settlement_batch）。
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final int MAX_CAS_RETRY = 3;
    private static final int DIRECTION_BUY = 1;

    private final TradeOrderMapper tradeOrderMapper;
    private final StockQuoteMapper stockQuoteMapper;
    private final StockInfoMapper stockInfoMapper;
    private final UserPositionMapper userPositionMapper;
    private final AccountService accountService;
    private final PositionService positionService;
    private final OrderService orderService;
    private final MatchEngine matchEngine;
    private final TransactionTemplate transactionTemplate;

    public SettlementService(TradeOrderMapper tradeOrderMapper, StockQuoteMapper stockQuoteMapper,
                             StockInfoMapper stockInfoMapper, UserPositionMapper userPositionMapper,
                             AccountService accountService, PositionService positionService,
                             OrderService orderService, MatchEngine matchEngine,
                             TransactionTemplate transactionTemplate) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.stockQuoteMapper = stockQuoteMapper;
        this.stockInfoMapper = stockInfoMapper;
        this.userPositionMapper = userPositionMapper;
        this.accountService = accountService;
        this.positionService = positionService;
        this.orderService = orderService;
        this.matchEngine = matchEngine;
        this.transactionTemplate = transactionTemplate;
    }

    public SettlementResult run() {
        LocalDate closeDate = currentQuoteTradeDate();
        if (closeDate == null) {
            log.info("无行情，跳过日切");
            return new SettlementResult(0, 0, 0, null, null);
        }
        LocalDate nextDate = TradeCalendar.nextTradingDay(closeDate);
        int expired = expireOrders(closeDate);
        int cutQuotes = cutQuotes(closeDate, nextDate);
        int cleared = clearTodayBuy();
        log.info("最小日切完成: closeDate={}, nextDate={}, 过期撤单={}, 行情日切={}, T+1清零={}",
                closeDate, nextDate, expired, cutQuotes, cleared);
        return new SettlementResult(expired, cutQuotes, cleared, closeDate, nextDate);
    }

    private LocalDate currentQuoteTradeDate() {
        StockQuote quote = stockQuoteMapper.selectOne(new LambdaQueryWrapper<StockQuote>().last("LIMIT 1"));
        return quote == null ? null : quote.getTradeDate();
    }

    /** 步骤1：按 idx_trade_date_status 扫收盘日未成交完订单，逐单股票锁内过期解冻 */
    private int expireOrders(LocalDate closeDate) {
        List<TradeOrder> openOrders = tradeOrderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getTradeDate, closeDate)
                        .in(TradeOrder::getStatus, OrderStateMachine.PENDING, OrderStateMachine.PARTIAL_FILLED)
                        .orderByAsc(TradeOrder::getStockCode, TradeOrder::getId));
        int expired = 0;
        for (TradeOrder order : openOrders) {
            if (expireOne(order.getOrderNo(), order.getStockCode(), closeDate)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOne(String orderNo, String stockCode, LocalDate closeDate) {
        ReentrantLock lock = matchEngine.lockOf(stockCode);
        lock.lock();
        try {
            for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
                try {
                    Boolean expired = transactionTemplate.execute(status -> {
                        TradeOrder order = tradeOrderMapper.selectOne(
                                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo));
                        if (order == null || !isOpen(order) || !closeDate.equals(order.getTradeDate())) {
                            return false;
                        }
                        if (order.getDirection() == DIRECTION_BUY) {
                            if (order.getFrozenAmount().signum() > 0) {
                                accountService.unfreeze(order.getUserId(), order.getFrozenAmount(),
                                        BizNoGenerator.cancelFund(orderNo), orderNo);
                            }
                        } else if (order.getFrozenQty() > 0) {
                            positionService.unfreeze(order.getUserId(), stockCode, order.getFrozenQty(),
                                    BizNoGenerator.cancelPosition(orderNo), orderNo);
                        }
                        TradeOrder update = new TradeOrder();
                        update.setRejectReason("日切过期撤单");
                        if (!orderService.casTransit(order, OrderStateMachine.EXPIRED, update)) {
                            throw new BizException(ErrorCode.SYSTEM_BUSY, "订单CAS冲突，待重试");
                        }
                        return true;
                    });
                    if (Boolean.TRUE.equals(expired)) {
                        matchEngine.bookOf(stockCode).remove(orderNo);
                        return true;
                    }
                    return false;
                } catch (BizException e) {
                    if (e.getErrorCode() == ErrorCode.SYSTEM_BUSY) {
                        continue;
                    }
                    throw e;
                }
            }
            throw new BizException(ErrorCode.SYSTEM_BUSY, "日切撤单并发冲突: " + orderNo);
        } finally {
            lock.unlock();
        }
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

    private static boolean isOpen(TradeOrder order) {
        return order.getStatus() == OrderStateMachine.PENDING
                || order.getStatus() == OrderStateMachine.PARTIAL_FILLED;
    }
}