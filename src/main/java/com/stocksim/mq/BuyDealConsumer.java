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
 * 买方顺序消费者（R2规划 Q5）：tag=BUY，order_no 顺序消费，复用 DealBookingService.consumeBuy（T3b）。
 * 幂等前置（B5 流水存在 → ACK）先于 requireOpenOrder，终态订单已记账重放不误入死信。
 * 顺序消费默认无限重试，显式 maxReconsumeTimes=5，耗尽 → 死信 + 告警。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${stocksim.mq.topic}",
        consumerGroup = "${stocksim.mq.buy-consumer-group}",
        selectorExpression = "${stocksim.mq.buy-tag}",
        consumeMode = ConsumeMode.ORDERLY)
public class BuyDealConsumer implements RocketMQListener<DealBookingMessage>, RocketMQPushConsumerLifecycleListener {

    private final DealBookingService bookingService;
    private final MqTestHooks testHooks;
    private final MqProperties mqProperties;

    public BuyDealConsumer(DealBookingService bookingService, MqTestHooks testHooks, MqProperties mqProperties) {
        this.bookingService = bookingService;
        this.testHooks = testHooks;
        this.mqProperties = mqProperties;
    }

    @Override
    public void onMessage(DealBookingMessage message) {
        if (testHooks.isConsumePaused()) {
            // test-mode 暂停：抛异常挂起当前队列，恢复后重试补齐记账（B6 在途窗口）
            throw new IllegalStateException("[test-mode] 买方消费暂停, dealNo=" + message.getDealNo());
        }
        bookingService.consumeBuy(message.toDealContext());
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setMaxReconsumeTimes(mqProperties.getMaxReconsumeTimes());
        if (mqProperties.isTestMode()) {
            consumer.setSuspendCurrentQueueTimeMillis(3000);
        }
    }
}