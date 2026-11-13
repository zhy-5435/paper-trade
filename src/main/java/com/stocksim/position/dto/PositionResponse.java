package com.stocksim.position.dto;

import java.math.BigDecimal;

public record PositionResponse(String stockCode, Long totalQty, Long frozenQty, Long todayBuyQty,
                               Long availableQty, BigDecimal costAmount, BigDecimal avgCostPrice,
                               BigDecimal latestPrice, BigDecimal marketValue, BigDecimal floatingPnl,
                               BigDecimal pnlRatio) {
}