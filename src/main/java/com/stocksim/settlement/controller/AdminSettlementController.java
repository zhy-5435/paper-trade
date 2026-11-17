package com.stocksim.settlement.controller;

import com.stocksim.common.Result;
import com.stocksim.common.audit.OperationAudit;
import com.stocksim.common.audit.OperationType;
import com.stocksim.settlement.dto.SettlementResult;
import com.stocksim.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-清算")
@RestController
@RequestMapping("/api/admin/settlement")
public class AdminSettlementController {

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "最小日切（过期撤单解冻→行情日切→T+1清零，幂等可重跑）")
    @OperationAudit(type = OperationType.OTHER, targetType = "settlement")
    @PostMapping("/run")
    public Result<SettlementResult> run() {
        return Result.ok(settlementService.run());
    }
}