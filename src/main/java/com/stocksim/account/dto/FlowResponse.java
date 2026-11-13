package com.stocksim.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FlowResponse(String flowNo, String requestId, Integer flowType, BigDecimal changeBalance,
                           BigDecimal changeFrozen, BigDecimal balanceBefore, BigDecimal balanceAfter,
                           BigDecimal frozenBefore, BigDecimal frozenAfter, Integer bizType, String bizNo,
                           String remark, LocalDateTime createTime) {
}