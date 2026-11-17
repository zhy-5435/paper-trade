package com.stocksim.mq;

import com.stocksim.common.properties.MqProperties;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.service.DealFactService;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * 成交事务消息本地执行器 + 回查（R2规划 Q4）。
 * BUY：T2' 幂等落 deal_record + 行情量额 → COMMIT；落库异常 → ROLLBACK（F1 未落库，引擎换新 dealNo 重试）。
 * SELL：确认 deal_record 存在 → COMMIT，不存在 → ROLLBACK。
 * 回查：只按 keys=dealNo 查 DB 存在性（不查内存），存在 COMMIT 否则 ROLLBACK。
 */
@Component
@RocketMQTransactionListener
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
public class DealTransactionListener implements RocketMQLocalTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(DealTransactionListener.class);

    private final DealFactService dealFactService;
    private final MqProperties mqProperties;
    private final MqTestHooks testHooks;

    public DealTransactionListener(DealFactService dealFactService, MqProperties mqProperties, MqTestHooks testHooks) {
        this.dealFactService = dealFactService;
        this.mqProperties = mqProperties;
        this.testHooks = testHooks;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        DealContext ctx = (DealContext) arg;
        // 适配器 convertToSpringMessage 给 header 加 rocketmq_ 前缀，须用 PREFIX+常量读取（裸 TAGS/KEYS 取到 null）
        String tag = (String) msg.getHeaders().get(RocketMQHeaders.PREFIX + RocketMQHeaders.TAGS);
        if (mqProperties.getBuyTag().equals(tag)) {
            try {
                dealFactService.recordDealFact(ctx);
                // test-mode：落库后一次性 UNKNOWN，模拟 F2（已落库发送不确定）触发回查 COMMIT
                if (testHooks.consumeUnknownOnce()) {
                    log.warn("[test-mode] BUY 落库后返回 UNKNOWN, dealNo={}", ctx.dealNo());
                    return RocketMQLocalTransactionState.UNKNOWN;
                }
                return RocketMQLocalTransactionState.COMMIT;
            } catch (Exception e) {
                log.error("BUY 成交事实落库失败(F1), dealNo={}", ctx.dealNo(), e);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        }
        // SELL：确认成交事实已落库即 COMMIT，否则 ROLLBACK
        return dealFactService.exists(ctx.dealNo())
                ? RocketMQLocalTransactionState.COMMIT
                : RocketMQLocalTransactionState.ROLLBACK;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String dealNo = (String) msg.getHeaders().get(RocketMQHeaders.PREFIX + RocketMQHeaders.KEYS);
        boolean exists = dealNo != null && dealFactService.exists(dealNo);
        log.info("事务回查, dealNo={}, exists={}", dealNo, exists);
        return exists ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }
}