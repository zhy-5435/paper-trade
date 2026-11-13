package com.stocksim.stock.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStockStatusRequest(@NotNull Integer status) {
}