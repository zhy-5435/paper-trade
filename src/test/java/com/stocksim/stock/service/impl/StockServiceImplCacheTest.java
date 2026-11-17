package com.stocksim.stock.service.impl;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.cache.CacheKeys;
import com.stocksim.common.cache.RedisCacheClient;
import com.stocksim.common.properties.CacheProperties;
import com.stocksim.stock.dto.CreateStockRequest;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.entity.StockQuote;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

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
class StockServiceImplCacheTest {

    private static final String CODE = "600000";

    @Mock
    private StockInfoMapper stockInfoMapper;
    @Mock
    private StockQuoteMapper stockQuoteMapper;
    @Mock
    private RedisCacheClient cacheClient;

    private StockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StockServiceImpl(stockInfoMapper, stockQuoteMapper, cacheClient, new CacheProperties());
    }

    private StockInfo stock() {
        StockInfo s = new StockInfo();
        s.setId(1L);
        s.setStockCode(CODE);
        s.setStockName("浦发银行");
        s.setMarket(1);
        s.setBoardType(1);
        s.setIssuePrice(new BigDecimal("10.00"));
        s.setPriceLimitRatio(new BigDecimal("0.10"));
        s.setLotSize(100);
        s.setListDate(LocalDate.of(2020, 1, 1));
        s.setStatus(1);
        return s;
    }

    private StockResponse response() {
        return new StockResponse(CODE, "浦发银行", 1, 1, new BigDecimal("10.00"),
                new BigDecimal("0.10"), 100, 1, LocalDate.of(2020, 1, 1));
    }

    @Test
    void 缓存命中不回源() {
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, CODE)).thenReturn(false);
        when(cacheClient.get(CacheKeys.stock(CODE), StockResponse.class)).thenReturn(response());

        StockResponse result = service.getStock(CODE);

        assertThat(result.stockCode()).isEqualTo(CODE);
        verifyNoInteractions(stockInfoMapper);
        verify(cacheClient, never()).set(anyString(), any(), anyLong());
    }

    @Test
    void 缓存未命中回源写缓存并补布隆() {
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, CODE)).thenReturn(false);
        when(cacheClient.get(CacheKeys.stock(CODE), StockResponse.class)).thenReturn(null);
        when(stockInfoMapper.selectOne(any())).thenReturn(stock());

        StockResponse result = service.getStock(CODE);

        assertThat(result.stockCode()).isEqualTo(CODE);
        verify(cacheClient).set(CacheKeys.stock(CODE), result, 1800000L);
        verify(cacheClient).bloomAdd(CacheKeys.BLOOM_STOCK, CODE);
    }

    @Test
    void 布隆判无拒绝且不回源() {
        when(cacheClient.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, "999999")).thenReturn(true);

        assertThatThrownBy(() -> service.getStock("999999"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
        verifyNoInteractions(stockInfoMapper);
    }

    @Test
    void 新增股票加入布隆() {
        when(stockInfoMapper.selectCount(any())).thenReturn(0L);
        CreateStockRequest request = new CreateStockRequest("600001", "新股票", 1, 1,
                new BigDecimal("10.00"), new BigDecimal("0.10"), 100, LocalDate.of(2026, 1, 1));

        service.createStock(request);

        verify(stockInfoMapper).insert(any(StockInfo.class));
        verify(stockQuoteMapper).insert(any(StockQuote.class));
        verify(cacheClient).bloomAdd(CacheKeys.BLOOM_STOCK, "600001");
    }

    @Test
    void 停牌状态变更延迟双删股票缓存() {
        when(stockInfoMapper.selectOne(any())).thenReturn(stock());
        when(stockInfoMapper.updateById(any(StockInfo.class))).thenReturn(1);

        service.updateStatus(CODE, 2);

        verify(cacheClient).evictDelayedDouble(CacheKeys.stock(CODE), 1000L);
    }
}