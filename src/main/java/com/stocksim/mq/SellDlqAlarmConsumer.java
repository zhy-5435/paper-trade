package com.stocksim.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 卖方死信告警消费者（R2规划 Q7）：订阅 %DLQ%stocksim-sell-consumer，重试耗尽的卖消息落死信 → 告警日志（B8 手动补偿）。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "%DLQ%${stocksim.mq.sell-consumer-group}",
        consumerGroup = "stocksim-sell-dlq-alarm")
public class SellDlqAlarmConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(SellDlqAlarmConsumer.class);

    @Override
    public void onMessage(String body) {
        log.error("[死信告警] 卖方记账重试耗尽进入死信, 需人工核查/补偿, body={}", body);
    }
}