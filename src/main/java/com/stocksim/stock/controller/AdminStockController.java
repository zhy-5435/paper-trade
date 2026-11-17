package com.stocksim.stock.controller;

import com.stocksim.common.Result;
import com.stocksim.common.audit.OperationAudit;
import com.stocksim.common.audit.OperationType;
import com.stocksim.stock.dto.CreateStockRequest;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.dto.UpdateStockRequest;
import com.stocksim.stock.dto.UpdateStockStatusRequest;
import com.stocksim.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-股票")
@RestController
@RequestMapping("/api/admin/stocks")
public class AdminStockController {

    private final StockService stockService;

    public AdminStockController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(summary = "新增股票（自动初始化当日行情）")
    @OperationAudit(type = OperationType.STOCK_MAINTAIN, targetType = "stock")
    @PostMapping
    public Result<StockResponse> create(@Valid @RequestBody CreateStockRequest request) {
        return Result.ok(stockService.createStock(request));
    }

    @Operation(summary = "修改股票")
    @OperationAudit(type = OperationType.STOCK_MAINTAIN, targetType = "stock")
    @PutMapping
    public Result<StockResponse> update(@Valid @RequestBody UpdateStockRequest request) {
        return Result.ok(stockService.updateStock(request));
    }

    @Operation(summary = "停牌/复牌/退市")
    @OperationAudit(type = OperationType.STOCK_MAINTAIN, targetType = "stock")
    @PutMapping("/{code}/status")
    public Result<Void> updateStatus(@PathVariable String code,
                                     @Valid @RequestBody UpdateStockStatusRequest request) {
        stockService.updateStatus(code, request.status());
        return Result.ok(null);
    }
}