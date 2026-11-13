package com.stocksim.order.event;

/** 下单事务提交后发布，match 模块 AFTER_COMMIT 监听触发撮合（P8，避免 order→match 反向依赖） */
public record OrderCreatedEvent(String stockCode, String orderNo) {
}