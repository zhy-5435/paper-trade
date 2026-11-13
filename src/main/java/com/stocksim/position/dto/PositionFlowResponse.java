package com.stocksim.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionFlowResponse(String flowNo, String stockCode, Integer changeType, Long changeQty,
                                   Long changeFrozen, BigDecimal dealPrice, Long totalQtyBefore, Long totalQtyAfter,
                                   Long frozenQtyBefore, Long frozenQtyAfter, BigDecimal costBefore,
                                   BigDecimal costAfter, String bizNo, LocalDateTime createTime) {
}