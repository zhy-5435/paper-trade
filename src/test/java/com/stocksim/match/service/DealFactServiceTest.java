package com.stocksim.match.service;

import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.stock.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealFactServiceTest {

    private static final String STOCK = "600000";

    @Mock
    private DealRecordMapper dealRecordMapper;
    @Mock
    private QuoteService quoteService;

    private DealFactService service;

    @BeforeEach
    void setUp() {
        service = new DealFactService(dealRecordMapper, quoteService);
    }

    private DealContext ctx() {
        return new DealContext("D1", 7L, STOCK, new BigDecimal("9.90"), 100, "B1", "S1", 1L, 2L);
    }

    @Test
    void recordDealFactInsertsAndAccumulatesQuote() {
        when(dealRecordMapper.countByDealNo("D1")).thenReturn(0);
        boolean inserted = service.recordDealFact(ctx());
        assertThat(inserted).isTrue();
        ArgumentCaptor<DealRecord> record = ArgumentCaptor.forClass(DealRecord.class);
        verify(dealRecordMapper).insert(record.capture());
        assertThat(record.getValue().getDealNo()).isEqualTo("D1");
        assertThat(record.getValue().getDealSeq()).isEqualTo(7L);
        assertThat(record.getValue().getDealQty()).isEqualTo(100);
        assertThat(record.getValue().getDealAmount()).isEqualByComparingTo("990.00");
        assertThat(record.getValue().getBuyerOrderNo()).isEqualTo("B1");
        assertThat(record.getValue().getSellerOrderNo()).isEqualTo("S1");
        verify(quoteService).accumulateDeal(STOCK, 100, new BigDecimal("990.00"));
    }

    @Test
    void recordDealFactIdempotentSkipsInsertAndQuote() {
        // Q4：已存在则跳过插入与量额累加，防重复累加行情
        when(dealRecordMapper.countByDealNo("D1")).thenReturn(1);
        boolean inserted = service.recordDealFact(ctx());
        assertThat(inserted).isFalse();
        verify(dealRecordMapper, never()).insert(any(DealRecord.class));
        verify(quoteService, never()).accumulateDeal(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void existsReflectsDbCount() {
        when(dealRecordMapper.countByDealNo("D1")).thenReturn(0);
        assertThat(service.exists("D1")).isFalse();
        when(dealRecordMapper.countByDealNo("D2")).thenReturn(1);
        assertThat(service.exists("D2")).isTrue();
    }
}