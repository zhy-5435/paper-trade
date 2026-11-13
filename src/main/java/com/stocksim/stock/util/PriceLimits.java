package com.stocksim.stock.util;

import com.stocksim.common.Amounts;

import java.math.BigDecimal;

public final class PriceLimits {

    private PriceLimits() {
    }

    public static BigDecimal up(BigDecimal preClose, BigDecimal ratio) {
        return Amounts.round2(preClose.multiply(BigDecimal.ONE.add(ratio)));
    }

    public static BigDecimal down(BigDecimal preClose, BigDecimal ratio) {
        return Amounts.round2(preClose.multiply(BigDecimal.ONE.subtract(ratio)));
    }
}