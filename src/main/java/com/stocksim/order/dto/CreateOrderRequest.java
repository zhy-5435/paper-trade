package com.stocksim.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String stockCode,
        @NotNull @Min(1) @Max(2) Integer direction,
        @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 4) BigDecimal price,
        @NotNull @Min(1) Long quantity) {
}