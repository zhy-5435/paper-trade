package com.stocksim.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Amounts {

    private Amounts() {
    }

    public static BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}