package com.stocksim.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrderResponse(String orderNo, String stockCode, Integer direction, Integer orderType,
                            BigDecimal entrustPrice, Long entrustQty, Long matchedQty, BigDecimal matchedAmount,
                            BigDecimal frozenAmount, Long frozenQty, BigDecimal feeAmount, Integer status,
                            LocalDate tradeDate, LocalDateTime expireTime, String rejectReason,
                            LocalDateTime createTime) {
}