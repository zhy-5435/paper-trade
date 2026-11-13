package com.stocksim.order.controller;

import com.stocksim.common.PageResult;
import com.stocksim.common.Result;
import com.stocksim.common.context.UserContext;
import com.stocksim.order.dto.CreateOrderRequest;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "订单")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "创建限价单（买/卖，requestId 幂等）")
    @PostMapping
    public Result<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.createOrder(UserContext.userId(), request));
    }

    @Operation(summary = "我的订单分页（状态/时间筛选）")
    @GetMapping
    public Result<PageResult<OrderResponse>> list(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.ok(orderService.listOrders(UserContext.userId(), page, size, status, startTime, endTime));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public Result<OrderResponse> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.getOrder(UserContext.userId(), orderNo));
    }
}