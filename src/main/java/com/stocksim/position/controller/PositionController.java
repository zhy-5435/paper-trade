package com.stocksim.position.controller;

import com.stocksim.common.PageResult;
import com.stocksim.common.Result;
import com.stocksim.common.context.UserContext;
import com.stocksim.position.dto.PositionFlowResponse;
import com.stocksim.position.dto.PositionResponse;
import com.stocksim.position.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "持仓")
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @Operation(summary = "持仓列表（实时市值/浮盈/可卖数量）")
    @GetMapping
    public Result<List<PositionResponse>> list() {
        return Result.ok(positionService.listPositions(UserContext.userId()));
    }

    @Operation(summary = "持仓流水分页")
    @GetMapping("/flows")
    public Result<PageResult<PositionFlowResponse>> flows(@RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size,
                                                          @RequestParam(required = false) String stockCode) {
        return Result.ok(positionService.listFlows(UserContext.userId(), page, size, stockCode));
    }
}