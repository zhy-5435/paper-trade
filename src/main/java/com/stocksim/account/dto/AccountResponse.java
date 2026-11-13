package com.stocksim.account.dto;

import java.math.BigDecimal;

public record AccountResponse(String accountNo, String currency, BigDecimal balance, BigDecimal frozenAmount,
                              BigDecimal availableAmount, BigDecimal totalAsset, Integer status) {
}