package com.stocksim.stock.service.impl;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceImplCacheTest {

    private static final String CODE = "600000";

    @Mock
    private StockInfoMapper stockInfoMapper;
    @Mock
    private StockQuoteMapper stockQuoteMapper;
    @Mock
    private RedisCacheClient cacheClient;

    private QuoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuoteServiceImpl(stockInfoMapper, stockQuoteMapper,
                new QuoteProperties(), new TradeProperties(), cacheClient, new CacheProperties());
    }

    private StockQuote quote() {
        StockQuote q = new StockQuote();
        q.setId(1L);
        q.setStockCode(CODE);
        q.setTradeDate(LocalDate.of(2026, 11, 16));
        q.setPreClosePrice(new BigDecimal("10.00"));
        q.setLatestPrice(new BigDecimal("10.00"));
        q.setLimitUpPrice(new BigDecimal("11.00"));
        q.setLimitDownPrice(new BigDecimal("9.00"));
        q.setTotalVolume(0L);
        q.setTotalAmount(BigDecimal.ZERO);
        q.setChangeRatio(BigDecimal.ZERO);
        q.setQuoteTime(LocalDateTime.of(2026, 11, 16, 9, 30));
        q.setVersion(0);
        return q;
    }

    @Test
    void 缓存命中不回源() {
        QuoteResponse cached = new QuoteResponse(CODE, LocalDate.of(2026, 11, 16),
                new BigDecimal("10.00"), null, new BigDecimal("10.02"), null, null,
                new BigDecimal("11.00"), new BigDecimal("9.00"), 0L, BigDecimal.ZERO,
                BigDecimal.ZERO, LocalDateTime.of(2026, 11, 16, 9, 30));
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, CODE)).thenReturn(false);
        when(cacheClient.get(CacheKeys.quote(CODE), QuoteResponse.class)).thenReturn(cached);

        QuoteResponse result = service.getQuote(CODE);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(stockQuoteMapper);
        verify(cacheClient, never()).set(anyString(), any(), anyLong());
    }

    @Test
    void 缓存未命中回源并写缓存TTL打散() {
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, CODE)).thenReturn(false);
        when(cacheClient.get(CacheKeys.quote(CODE), QuoteResponse.class)).thenReturn(null);
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote());

        QuoteResponse result = service.getQuote(CODE);

        assertThat(result.stockCode()).isEqualTo(CODE);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(cacheClient).set(eq(CacheKeys.quote(CODE)), any(QuoteResponse.class), ttl.capture());
        assertThat(ttl.getValue()).isBetween(2000L, 4000L);
    }

    @Test
    void 布隆判无拒绝且不回源() {
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, "999999")).thenReturn(true);

        assertThatThrownBy(() -> service.getQuote("999999"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
        verifyNoInteractions(stockQuoteMapper);
        verify(cacheClient, never()).get(anyString(), any());
    }

    @Test
    void 回源DB无行情抛1001() {
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, CODE)).thenReturn(false);
        when(cacheClient.get(CacheKeys.quote(CODE), QuoteResponse.class)).thenReturn(null);
        when(stockQuoteMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getQuote(CODE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    void 刷新成功失效行情缓存() {
        StockInfo stock = new StockInfo();
        stock.setStockCode(CODE);
        stock.setStatus(1);
        when(stockInfoMapper.selectList(any())).thenReturn(List.of(stock));
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote());
        when(stockQuoteMapper.updateById(any(StockQuote.class))).thenReturn(1);

        int refreshed = service.refreshAll(true);

        assertThat(refreshed).isEqualTo(1);
        verify(cacheClient).delete(CacheKeys.quote(CODE));
    }
}