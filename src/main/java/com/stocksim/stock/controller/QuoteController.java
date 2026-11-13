package com.stocksim.stock.controller;

import com.stocksim.common.Result;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "行情")
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Operation(summary = "批量行情查询")
    @GetMapping
    public Result<List<QuoteResponse>> batch(@RequestParam List<String> codes) {
        return Result.ok(quoteService.batchQuotes(codes));
    }
}