package com.stocksim.mq;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.notify.DealNotify;
import com.stocksim.match.service.DealFactService;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 异步回报（notify=mq，R2规划 Q4）。不变量：deal_record 落库即成交事实。
 * onDeal 正常返回 ⟺ 成交事实已落库（引擎据此扣盘口、推进 dealSeq）；抛 MATCH_CONFLICT ⟺ F1 未落库（引擎换新 dealNo 重试）。
 * F1 未落库（半消息失败/执行器 ROLLBACK）→ 抛 MATCH_CONFLICT；
 * F2 已落库但发送不确定（UNKNOWN/end-ack 失败/SELL 发送失败）→ 不换 dealNo、正常返回，回查 + Q7 补偿兜底。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
public class MqDealNotify implements DealNotify {

    private static final Logger log = LoggerFactory.getLogger(MqDealNotify.class);

    private final DealMessageProducer producer;
    private final DealFactService dealFactService;

    public MqDealNotify(DealMessageProducer producer, DealFactService dealFactService) {
        this.producer = producer;
        this.dealFactService = dealFactService;
    }

    @Override
    public void onDeal(DealContext ctx) {
        boolean factCommitted;
        try {
            TransactionSendResult result = producer.sendBuy(ctx);
            LocalTransactionState state = result == null ? null : result.getLocalTransactionState();
            if (state == LocalTransactionState.COMMIT_MESSAGE) {
                factCommitted = true;
            } else if (state == LocalTransactionState.ROLLBACK_MESSAGE) {
                factCommitted = false;
            } else {
                // UNKNOWN/发送不确定：按 dealNo 查库归类 F1/F2（只查 DB 不查内存）
                factCommitted = dealFactService.exists(ctx.dealNo());
            }
        } catch (Exception e) {
            log.warn("BUY 事务消息发送异常, 按 dealNo 查库归类, dealNo={}", ctx.dealNo(), e);
            factCommitted = dealFactService.exists(ctx.dealNo());
        }
        if (!factCommitted) {
            // F1：成交事实未落库，可重试（引擎换新 dealNo/seq）
            throw new BizException(ErrorCode.MATCH_CONFLICT, "成交事实未落库(F1)，待重试");
        }
        // 成交事实成立：发 SELL。失败不回滚事实、不换 dealNo（F2），缺失消息由回查 + 补偿兜底
        try {
            producer.sendSell(ctx);
        } catch (Exception e) {
            log.warn("SELL 事务消息发送失败(F2), 回查/补偿兜底, dealNo={}", ctx.dealNo(), e);
        }
    }
}