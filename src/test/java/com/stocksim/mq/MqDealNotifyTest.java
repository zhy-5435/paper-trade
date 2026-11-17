package com.stocksim.mq;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.service.DealFactService;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqDealNotifyTest {

    @Mock
    private DealMessageProducer producer;
    @Mock
    private DealFactService dealFactService;

    private MqDealNotify notify;

    @BeforeEach
    void setUp() {
        notify = new MqDealNotify(producer, dealFactService);
    }

    private DealContext ctx() {
        return new DealContext("D1", 1L, "600000", new BigDecimal("9.90"), 100, "B1", "S1", 1L, 2L);
    }

    private TransactionSendResult result(LocalTransactionState state) {
        TransactionSendResult r = new TransactionSendResult();
        r.setLocalTransactionState(state);
        return r;
    }

    @Test
    void buyCommitSendsSellAndReturns() {
        when(producer.sendBuy(any())).thenReturn(result(LocalTransactionState.COMMIT_MESSAGE));
        notify.onDeal(ctx());
        verify(producer).sendSell(any());
    }

    @Test
    void buyRollbackIsF1ThrowsConflictNoSell() {
        when(producer.sendBuy(any())).thenReturn(result(LocalTransactionState.ROLLBACK_MESSAGE));
        assertThatThrownBy(() -> notify.onDeal(ctx()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
        verify(producer, never()).sendSell(any());
    }

    @Test
    void buyUnknownButFactExistsIsF2ReturnsAndSendsSell() {
        when(producer.sendBuy(any())).thenReturn(result(LocalTransactionState.UNKNOW));
        when(dealFactService.exists("D1")).thenReturn(true);
        notify.onDeal(ctx());
        verify(producer).sendSell(any());
    }

    @Test
    void buyUnknownAndFactAbsentIsF1Throws() {
        when(producer.sendBuy(any())).thenReturn(result(LocalTransactionState.UNKNOW));
        when(dealFactService.exists("D1")).thenReturn(false);
        assertThatThrownBy(() -> notify.onDeal(ctx()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
        verify(producer, never()).sendSell(any());
    }

    @Test
    void buySendThrowsAndFactAbsentIsF1() {
        when(producer.sendBuy(any())).thenThrow(new RuntimeException("半消息发送失败"));
        when(dealFactService.exists("D1")).thenReturn(false);
        assertThatThrownBy(() -> notify.onDeal(ctx()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
    }

    @Test
    void buySendThrowsButFactExistsIsF2Returns() {
        when(producer.sendBuy(any())).thenThrow(new RuntimeException("end-ack 失败"));
        when(dealFactService.exists("D1")).thenReturn(true);
        notify.onDeal(ctx());
        verify(producer).sendSell(any());
    }

    @Test
    void sellSendFailureIsF2NormalReturn() {
        // 成交事实已落库，SELL 发送失败不回滚事实、不换 dealNo、正常返回（回查/补偿兜底）
        when(producer.sendBuy(any())).thenReturn(result(LocalTransactionState.COMMIT_MESSAGE));
        when(producer.sendSell(any())).thenThrow(new RuntimeException("SELL 发送失败"));
        notify.onDeal(ctx());
        verify(producer).sendSell(any());
    }

    @Test
    void buyNullResultAndFactAbsentIsF1() {
        when(producer.sendBuy(any())).thenReturn(null);
        when(dealFactService.exists("D1")).thenReturn(false);
        assertThatThrownBy(() -> notify.onDeal(ctx()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
    }
}