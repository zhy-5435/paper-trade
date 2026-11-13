package com.stocksim.stock.controller;

import com.stocksim.common.Result;
import com.stocksim.stock.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-行情")
@RestController
@RequestMapping("/api/admin/quotes")
public class AdminQuoteController {

    private final QuoteService quoteService;

    public AdminQuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Operation(summary = "手动刷新全部行情（返回刷新只数）")
    @PostMapping("/refresh")
    public Result<Integer> refresh() {
        return Result.ok(quoteService.refreshAll(true));
    }
}