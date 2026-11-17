package com.stocksim.mq;

import com.stocksim.common.properties.MqProperties;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.service.DealFactService;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealTransactionListenerTest {

    @Mock
    private DealFactService dealFactService;

    private MqProperties mqProperties;
    private MqTestHooks testHooks;
    private DealTransactionListener listener;

    @BeforeEach
    void setUp() {
        mqProperties = new MqProperties();
        testHooks = new MqTestHooks(mqProperties);
        listener = new DealTransactionListener(dealFactService, mqProperties, testHooks);
    }

    private DealContext ctx() {
        return new DealContext("D1", 1L, "600000", new BigDecimal("9.90"), 100, "B1", "S1", 1L, 2L);
    }

    private Message<byte[]> tagged(String tag) {
        // 真实适配器 convertToSpringMessage 给 header 加 rocketmq_ 前缀
        return MessageBuilder.withPayload(new byte[0])
                .setHeader(RocketMQHeaders.PREFIX + RocketMQHeaders.TAGS, tag).build();
    }

    private Message<byte[]> keyed(String dealNo) {
        return MessageBuilder.withPayload(new byte[0])
                .setHeader(RocketMQHeaders.PREFIX + RocketMQHeaders.KEYS, dealNo).build();
    }

    @Test
    void buyExecutorRecordsFactAndCommits() {
        when(dealFactService.recordDealFact(any())).thenReturn(true);
        RocketMQLocalTransactionState state = listener.executeLocalTransaction(tagged("BUY"), ctx());
        assertThat(state).isEqualTo(RocketMQLocalTransactionState.COMMIT);
        verify(dealFactService).recordDealFact(any());
    }

    @Test
    void buyExecutorIdempotentAlreadyExistsStillCommits() {
        when(dealFactService.recordDealFact(any())).thenReturn(false);
        assertThat(listener.executeLocalTransaction(tagged("BUY"), ctx()))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
    }

    @Test
    void buyExecutorRecordFailureRollsBack() {
        doThrow(new RuntimeException("DB 异常")).when(dealFactService).recordDealFact(any());
        assertThat(listener.executeLocalTransaction(tagged("BUY"), ctx()))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    @Test
    void buyExecutorTestModeUnknownOnceAfterFact() {
        mqProperties.setTestMode(true);
        testHooks.armUnknownOnce();
        when(dealFactService.recordDealFact(any())).thenReturn(true);
        // F2 模拟：落库后返回 UNKNOWN，回查将 COMMIT
        assertThat(listener.executeLocalTransaction(tagged("BUY"), ctx()))
                .isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
        verify(dealFactService).recordDealFact(any());
        // 一次性：第二次恢复 COMMIT
        assertThat(listener.executeLocalTransaction(tagged("BUY"), ctx()))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
    }

    @Test
    void sellExecutorConfirmsFactExistence() {
        when(dealFactService.exists("D1")).thenReturn(true);
        assertThat(listener.executeLocalTransaction(tagged("SELL"), ctx()))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
        when(dealFactService.exists("D1")).thenReturn(false);
        assertThat(listener.executeLocalTransaction(tagged("SELL"), ctx()))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    @Test
    void checkLocalTransactionCommitsWhenFactExists() {
        when(dealFactService.exists("D1")).thenReturn(true);
        assertThat(listener.checkLocalTransaction(keyed("D1")))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
    }

    @Test
    void checkLocalTransactionRollsBackWhenFactAbsent() {
        when(dealFactService.exists("D1")).thenReturn(false);
        assertThat(listener.checkLocalTransaction(keyed("D1")))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    @Test
    void checkLocalTransactionNullKeysRollsBack() {
        assertThat(listener.checkLocalTransaction(keyed(null)))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }
}