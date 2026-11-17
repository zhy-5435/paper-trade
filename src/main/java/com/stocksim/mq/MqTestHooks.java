package com.stocksim.mq;

import com.stocksim.common.properties.MqProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQ 测试钩子（R2规划 Q5，stocksim.mq.test-mode 默认关）：支撑 B6 在途撤单 / B10 回查手动用例。
 * - consumePaused：消费者暂停（抛异常挂起队列，恢复后补齐记账）
 * - unknownOnce：BUY 本地执行器落库后一次性返回 UNKNOWN，模拟 F2 触发回查 COMMIT
 * test-mode 关闭时所有钩子恒为无操作，生产路径零影响。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
public class MqTestHooks {

    private final MqProperties mqProperties;
    private final AtomicBoolean consumePaused = new AtomicBoolean(false);
    private final AtomicBoolean unknownOnce = new AtomicBoolean(false);

    public MqTestHooks(MqProperties mqProperties) {
        this.mqProperties = mqProperties;
    }

    public boolean isConsumePaused() {
        return mqProperties.isTestMode() && consumePaused.get();
    }

    public void pauseConsume() {
        if (mqProperties.isTestMode()) {
            consumePaused.set(true);
        }
    }

    public void resumeConsume() {
        consumePaused.set(false);
    }

    /** 取走一次性 UNKNOWN 标记（仅 test-mode）：BUY 执行器落库后返回 UNKNOWN 触发回查 */
    public boolean consumeUnknownOnce() {
        return mqProperties.isTestMode() && unknownOnce.getAndSet(false);
    }

    public void armUnknownOnce() {
        if (mqProperties.isTestMode()) {
            unknownOnce.set(true);
        }
    }
}