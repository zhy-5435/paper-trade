package com.stocksim.mq;

import com.stocksim.common.properties.MqProperties;
import com.stocksim.match.service.DealBookingService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 卖方顺序消费者（R2规划 Q5）：tag=SELL，order_no 顺序消费，复用 DealBookingService.consumeSell（T3s）。
 * 幂等前置（S6 流水存在 → ACK）先于 requireOpenOrder；与买方不同消费组天然并行。
 * 顺序消费显式 maxReconsumeTimes=5，耗尽 → 死信 + 告警。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${stocksim.mq.topic}",
        consumerGroup = "${stocksim.mq.sell-consumer-group}",
        selectorExpression = "${stocksim.mq.sell-tag}",
        consumeMode = ConsumeMode.ORDERLY)
public class SellDealConsumer implements RocketMQListener<DealBookingMessage>, RocketMQPushConsumerLifecycleListener {

    private final DealBookingService bookingService;
    private final MqTestHooks testHooks;
    private final MqProperties mqProperties;

    public SellDealConsumer(DealBookingService bookingService, MqTestHooks testHooks, MqProperties mqProperties) {
        this.bookingService = bookingService;
        this.testHooks = testHooks;
        this.mqProperties = mqProperties;
    }

    @Override
    public void onMessage(DealBookingMessage message) {
        if (testHooks.isConsumePaused()) {
            throw new IllegalStateException("[test-mode] 卖方消费暂停, dealNo=" + message.getDealNo());
        }
        bookingService.consumeSell(message.toDealContext());
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setMaxReconsumeTimes(mqProperties.getMaxReconsumeTimes());
        if (mqProperties.isTestMode()) {
            consumer.setSuspendCurrentQueueTimeMillis(3000);
        }
    }
}