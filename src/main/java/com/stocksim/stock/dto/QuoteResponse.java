package com.stocksim.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record QuoteResponse(String stockCode, LocalDate tradeDate, BigDecimal preClosePrice,
                            BigDecimal openPrice, BigDecimal latestPrice, BigDecimal highPrice,
                            BigDecimal lowPrice, BigDecimal limitUpPrice, BigDecimal limitDownPrice,
                            Long totalVolume, BigDecimal totalAmount, BigDecimal changeRatio,
                            LocalDateTime quoteTime) {
}