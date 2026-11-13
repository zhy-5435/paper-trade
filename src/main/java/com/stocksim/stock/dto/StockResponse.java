package com.stocksim.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockResponse(String stockCode, String stockName, Integer market, Integer boardType,
                            BigDecimal issuePrice, BigDecimal priceLimitRatio, Integer lotSize,
                            Integer status, LocalDate listDate) {
}