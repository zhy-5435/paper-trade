package com.stocksim.order.service;

import com.stocksim.common.PageResult;
import com.stocksim.order.dto.CreateOrderRequest;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.entity.TradeOrder;

import java.time.LocalDateTime;

public interface OrderService {

    OrderResponse createOrder(Long userId, CreateOrderRequest request);

    PageResult<OrderResponse> listOrders(Long userId, long page, long size, Integer status,
                                         LocalDateTime startTime, LocalDateTime endTime);

    OrderResponse getOrder(Long userId, String orderNo);

    /**
     * 状态机 CAS 流转（撮合/撤单/清算复用）：update 携带业务字段（id/version/status 由本方法设置），
     * 非法迁移抛 BizException，版本冲突返回 false 由调用方重试或留单。
     * 成功流转同事务写 order_status_log：changeType 1撮合 2用户撤单 3过期撤单 4清算；系统操作 operatorId 为空。
     */
    boolean casTransit(TradeOrder current, int toStatus, TradeOrder update, int changeType, Long operatorId);
}