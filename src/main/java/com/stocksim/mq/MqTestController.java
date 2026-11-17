package com.stocksim.mq;

import com.stocksim.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MQ 测试钩子端点（R2规划 Q5，仅 stocksim.mq.test-mode=true 时存在，需 notify=mq）。
 * 支撑 B6 在途撤单（暂停消费）/ B10 回查（一次性 UNKNOWN）手动用例；走 /api/admin/** 鉴权（role=2）。
 */
@Tag(name = "管理端-MQ测试钩子")
@RestController
@RequestMapping("/api/admin/mq/test")
@ConditionalOnProperty(prefix = "stocksim.mq", name = "test-mode", havingValue = "true")
public class MqTestController {

    private final MqTestHooks testHooks;
    private final DealCompensateTask compensateTask;

    public MqTestController(MqTestHooks testHooks, DealCompensateTask compensateTask) {
        this.testHooks = testHooks;
        this.compensateTask = compensateTask;
    }

    @Operation(summary = "暂停买/卖消费（挂起队列，制造在途记账窗口）")
    @PostMapping("/pause")
    public Result<String> pause() {
        testHooks.pauseConsume();
        return Result.ok("paused");
    }

    @Operation(summary = "恢复买/卖消费（重试补齐记账）")
    @PostMapping("/resume")
    public Result<String> resume() {
        testHooks.resumeConsume();
        return Result.ok("resumed");
    }

    @Operation(summary = "下一次 BUY 本地执行器落库后返回 UNKNOWN（模拟 F2 触发回查）")
    @PostMapping("/unknown-once")
    public Result<String> unknownOnce() {
        testHooks.armUnknownOnce();
        return Result.ok("armed");
    }

    @Operation(summary = "手动触发补偿任务（扫近窗口 deal_record 补发缺失记账）")
    @PostMapping("/compensate")
    public Result<String> compensate() {
        compensateTask.compensate();
        return Result.ok("compensated");
    }
}