package com.stocksim.match.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DealResponse(String dealNo, String stockCode, Integer direction, String orderNo,
                           BigDecimal dealPrice, Long dealQty, BigDecimal dealAmount, Long dealSeq,
                           LocalDateTime dealTime) {
}