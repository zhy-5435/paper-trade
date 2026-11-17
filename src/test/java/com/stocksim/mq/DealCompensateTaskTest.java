package com.stocksim.mq;

import com.stocksim.common.properties.MqProperties;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.match.service.DealBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealCompensateTaskTest {

    @Mock
    private DealRecordMapper dealRecordMapper;
    @Mock
    private DealBookingService bookingService;
    @Mock
    private DealMessageProducer producer;

    private DealCompensateTask task;

    @BeforeEach
    void setUp() {
        task = new DealCompensateTask(dealRecordMapper, bookingService, producer, new MqProperties());
    }

    private DealRecord record() {
        DealRecord r = new DealRecord();
        r.setDealNo("D1");
        r.setDealSeq(1L);
        r.setStockCode("600000");
        r.setDealPrice(new BigDecimal("9.90"));
        r.setDealQty(100L);
        r.setDealAmount(new BigDecimal("990.00"));
        r.setBuyerOrderNo("B1");
        r.setSellerOrderNo("S1");
        r.setBuyerUserId(1L);
        r.setSellerUserId(2L);
        r.setDealTime(LocalDateTime.now());
        return r;
    }

    @Test
    void resendsOnlyMissingBuySide() {
        when(dealRecordMapper.selectList(any())).thenReturn(List.of(record()));
        when(bookingService.alreadyBooked("D1", true)).thenReturn(false);
        when(bookingService.alreadyBooked("D1", false)).thenReturn(true);
        task.compensate();
        verify(producer).resendOrderly(any(DealContext.class), eq(true));
        verify(producer, never()).resendOrderly(any(DealContext.class), eq(false));
    }

    @Test
    void resendsBothSidesWhenMissing() {
        when(dealRecordMapper.selectList(any())).thenReturn(List.of(record()));
        when(bookingService.alreadyBooked("D1", true)).thenReturn(false);
        when(bookingService.alreadyBooked("D1", false)).thenReturn(false);
        task.compensate();
        verify(producer).resendOrderly(any(DealContext.class), eq(true));
        verify(producer).resendOrderly(any(DealContext.class), eq(false));
    }

    @Test
    void noResendWhenBothBooked() {
        when(dealRecordMapper.selectList(any())).thenReturn(List.of(record()));
        when(bookingService.alreadyBooked("D1", true)).thenReturn(true);
        when(bookingService.alreadyBooked("D1", false)).thenReturn(true);
        task.compensate();
        verify(producer, never()).resendOrderly(any(DealContext.class), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void emptyWindowNoResend() {
        when(dealRecordMapper.selectList(any())).thenReturn(List.of());
        task.compensate();
        verify(producer, never()).resendOrderly(any(DealContext.class), org.mockito.ArgumentMatchers.anyBoolean());
    }
}