package com.stocksim.mq;

import com.stocksim.common.properties.MqProperties;
import com.stocksim.match.engine.DealContext;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 成交记账消息生产者（R2规划 Q4/Q7）。
 * BUY/SELL 走事务消息（半消息 → 本地执行器 → COMMIT/ROLLBACK → 回查），keys=dealNo。
 * 注：RocketMQ 事务消息不支持 MessageQueueSelector（客户端 sendMessageInTransaction 走轮询选队列），
 * 故同订单顺序性由消费端 ORDERLY + 幂等前置 + 版本 CAS 保证；补偿补发为普通消息，用 order_no 分片键顺序发送。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
public class DealMessageProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final MqProperties mqProperties;

    public DealMessageProducer(RocketMQTemplate rocketMQTemplate, MqProperties mqProperties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.mqProperties = mqProperties;
    }

    public TransactionSendResult sendBuy(DealContext ctx) {
        return sendTransaction(ctx, mqProperties.getBuyTag());
    }

    public TransactionSendResult sendSell(DealContext ctx) {
        return sendTransaction(ctx, mqProperties.getSellTag());
    }

    private TransactionSendResult sendTransaction(DealContext ctx, String tag) {
        String destination = mqProperties.getTopic() + ":" + tag;
        Message<DealBookingMessage> message = MessageBuilder.withPayload(DealBookingMessage.from(ctx))
                .setHeader(RocketMQHeaders.KEYS, ctx.dealNo())
                .build();
        // arg=ctx 仅本地执行器使用（同 JVM，不序列化）
        return rocketMQTemplate.sendMessageInTransaction(destination, message, ctx);
    }

    /** 补偿补发（Q7）：普通顺序消息，order_no 作分片键 → 同订单顺序消费；消费幂等兜底 */
    public void resendOrderly(DealContext ctx, boolean buy) {
        String tag = buy ? mqProperties.getBuyTag() : mqProperties.getSellTag();
        String destination = mqProperties.getTopic() + ":" + tag;
        String hashKey = buy ? ctx.buyerOrderNo() : ctx.sellerOrderNo();
        Message<DealBookingMessage> message = MessageBuilder.withPayload(DealBookingMessage.from(ctx))
                .setHeader(RocketMQHeaders.KEYS, ctx.dealNo())
                .build();
        rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
    }
}