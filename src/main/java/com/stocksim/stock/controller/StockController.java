package com.stocksim.stock.controller;

import com.stocksim.common.PageResult;
import com.stocksim.common.Result;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.service.QuoteService;
import com.stocksim.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "股票")
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final QuoteService quoteService;

    public StockController(StockService stockService, QuoteService quoteService) {
        this.stockService = stockService;
        this.quoteService = quoteService;
    }

    @Operation(summary = "股票分页列表")
    @GetMapping
    public Result<PageResult<StockResponse>> list(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.ok(stockService.listStocks(page, size));
    }

    @Operation(summary = "股票详情")
    @GetMapping("/{code}")
    public Result<StockResponse> detail(@PathVariable String code) {
        return Result.ok(stockService.getStock(code));
    }

    @Operation(summary = "单只股票行情")
    @GetMapping("/{code}/quote")
    public Result<QuoteResponse> quote(@PathVariable String code) {
        return Result.ok(quoteService.getQuote(code));
    }
}