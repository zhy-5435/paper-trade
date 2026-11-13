package com.stocksim.match.controller;

import com.stocksim.common.PageResult;
import com.stocksim.common.Result;
import com.stocksim.common.context.UserContext;
import com.stocksim.match.dto.DealResponse;
import com.stocksim.match.service.DealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "成交")
@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @Operation(summary = "我的成交分页（可按股票代码筛选）")
    @GetMapping
    public Result<PageResult<DealResponse>> list(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String stockCode) {
        return Result.ok(dealService.listDeals(UserContext.userId(), page, size, stockCode));
    }
}