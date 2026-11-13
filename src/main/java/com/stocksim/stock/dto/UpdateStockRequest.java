package com.stocksim.stock.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateStockRequest(
        @NotBlank String stockCode,
        @Size(max = 32) String stockName,
        @Min(1) @Max(3) Integer market,
        @Min(1) @Max(4) Integer boardType,
        @DecimalMin("0.01") @DecimalMax("0.50") BigDecimal priceLimitRatio,
        @Min(1) Integer lotSize,
        LocalDate listDate) {
}