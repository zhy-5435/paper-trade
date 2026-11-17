package com.stocksim.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.common.Amounts;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.TradeCalendar;
import com.stocksim.common.TradingSession;
import com.stocksim.common.cache.CacheKeys;
import com.stocksim.common.cache.RedisCacheClient;
import com.stocksim.common.properties.CacheProperties;
import com.stocksim.common.properties.QuoteProperties;
import com.stocksim.common.properties.TradeProperties;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.entity.StockQuote;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import com.stocksim.stock.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QuoteServiceImpl implements QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteServiceImpl.class);
    private static final int MAX_CAS_RETRY = 3;

    private final StockInfoMapper stockInfoMapper;
    private final StockQuoteMapper stockQuoteMapper;
    private final QuoteProperties quoteProperties;
    private final TradeProperties tradeProperties;
    private final RedisCacheClient cacheClient;
    private final CacheProperties cacheProperties;

    public QuoteServiceImpl(StockInfoMapper stockInfoMapper, StockQuoteMapper stockQuoteMapper,
                            QuoteProperties quoteProperties, TradeProperties tradeProperties,
                            RedisCacheClient cacheClient, CacheProperties cacheProperties) {
        this.stockInfoMapper = stockInfoMapper;
        this.stockQuoteMapper = stockQuoteMapper;
        this.quoteProperties = quoteProperties;
        this.tradeProperties = tradeProperties;
        this.cacheClient = cacheClient;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public QuoteResponse getQuote(String stockCode) {
        if (cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, stockCode)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "行情不存在");
        }
        String key = CacheKeys.quote(stockCode);
        QuoteResponse cached = cacheClient.get(key, QuoteResponse.class);
        if (cached != null) {
            log.debug("行情缓存命中, stockCode={}", stockCode);
            return cached;
        }
        StockQuote quote = stockQuoteMapper.selectOne(
                new LambdaQueryWrapper<StockQuote>().eq(StockQuote::getStockCode, stockCode));
        if (quote == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "行情不存在");
        }
        QuoteResponse resp = toResponse(quote);
        cacheClient.set(key, resp, quoteTtlWithJitter());
        log.debug("行情缓存回源, stockCode={}", stockCode);
        return resp;
    }

    /** 行情短 TTL + 随机打散防雪崩（Q1，对齐刷新间隔） */
    private long quoteTtlWithJitter() {
        long base = cacheProperties.getQuoteTtlMs();
        long jitter = cacheProperties.getQuoteTtlJitterMs();
        long delta = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Math.max(1, base + delta);
    }

    @Override
    public List<QuoteResponse> batchQuotes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<StockQuote> quotes = stockQuoteMapper.selectList(
                new LambdaQueryWrapper<StockQuote>().in(StockQuote::getStockCode, codes));
        return quotes.stream().map(this::toResponse).toList();
    }

    @Override
    public int refreshAll(boolean ignoreSession) {
        if (!ignoreSession && tradeProperties.isSessionCheckEnabled()
                && !TradingSession.inSession(LocalDateTime.now(TradeCalendar.ZONE))) {
            return 0;
        }
        List<StockInfo> listed = stockInfoMapper.selectList(
                new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getStatus, 1));
        int refreshed = 0;
        for (StockInfo stock : listed) {
            if (refreshOne(stock.getStockCode())) {
                refreshed++;
            }
        }
        return refreshed;
    }

    @Override
    @Transactional
    public void accumulateDeal(String stockCode, long qty, BigDecimal amount) {
        StockQuote quote = stockQuoteMapper.selectOne(
                new LambdaQueryWrapper<StockQuote>().eq(StockQuote::getStockCode, stockCode));
        if (quote == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "行情不存在");
        }
        StockQuote update = new StockQuote();
        update.setId(quote.getId());
        update.setVersion(quote.getVersion());
        update.setTotalVolume(quote.getTotalVolume() + qty);
        update.setTotalAmount(quote.getTotalAmount().add(amount));
        if (stockQuoteMapper.updateById(update) == 0) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "行情量额并发冲突，请重试");
        }
    }

    private boolean refreshOne(String stockCode) {
        for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
            StockQuote quote = stockQuoteMapper.selectOne(
                    new LambdaQueryWrapper<StockQuote>().eq(StockQuote::getStockCode, stockCode));
            if (quote == null) {
                return false;
            }
            BigDecimal next = nextPrice(quote.getLatestPrice(), quoteProperties.getWalkStepRatio(),
                    quote.getLimitUpPrice(), quote.getLimitDownPrice(),
                    ThreadLocalRandom.current().nextDouble(-1, 1));
            StockQuote update = new StockQuote();
            update.setId(quote.getId());
            update.setVersion(quote.getVersion());
            update.setLatestPrice(next);
            update.setOpenPrice(quote.getOpenPrice() == null ? next : quote.getOpenPrice());
            update.setHighPrice(quote.getHighPrice() == null ? next : quote.getHighPrice().max(next));
            update.setLowPrice(quote.getLowPrice() == null ? next : quote.getLowPrice().min(next));
            update.setChangeRatio(changeRatio(next, quote.getPreClosePrice()));
            update.setQuoteTime(LocalDateTime.now(TradeCalendar.ZONE));
            if (stockQuoteMapper.updateById(update) > 0) {
                cacheClient.delete(CacheKeys.quote(stockCode));
                return true;
            }
        }
        log.warn("行情刷新CAS冲突放弃, stockCode={}", stockCode);
        return false;
    }

    static BigDecimal nextPrice(BigDecimal latest, BigDecimal stepRatio,
                                BigDecimal limitUp, BigDecimal limitDown, double rand) {
        BigDecimal delta = latest.multiply(stepRatio).multiply(BigDecimal.valueOf(rand));
        BigDecimal next = Amounts.round2(latest.add(delta));
        return next.max(limitDown).min(limitUp);
    }

    static BigDecimal changeRatio(BigDecimal latest, BigDecimal preClose) {
        if (preClose.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return latest.subtract(preClose).divide(preClose, 4, RoundingMode.HALF_UP);
    }

    private QuoteResponse toResponse(StockQuote quote) {
        return new QuoteResponse(quote.getStockCode(), quote.getTradeDate(), quote.getPreClosePrice(),
                quote.getOpenPrice(), quote.getLatestPrice(), quote.getHighPrice(), quote.getLowPrice(),
                quote.getLimitUpPrice(), quote.getLimitDownPrice(), quote.getTotalVolume(),
                quote.getTotalAmount(), quote.getChangeRatio(), quote.getQuoteTime());
    }
}