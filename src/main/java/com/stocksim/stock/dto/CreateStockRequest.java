package com.stocksim.stock.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateStockRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String stockCode,
        @NotBlank @Size(max = 32) String stockName,
        @NotNull @Min(1) @Max(3) Integer market,
        @NotNull @Min(1) @Max(4) Integer boardType,
        @NotNull @DecimalMin("0.01") BigDecimal issuePrice,
        @NotNull @DecimalMin("0.01") @DecimalMax("0.50") BigDecimal priceLimitRatio,
        @Min(1) Integer lotSize,
        LocalDate listDate) {
}