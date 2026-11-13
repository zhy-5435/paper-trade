package com.stocksim.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountsTest {

    @Test
    void roundsHalfUpToFen() {
        assertThat(Amounts.round2(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Amounts.round2(new BigDecimal("1.004"))).isEqualByComparingTo("1.00");
        assertThat(Amounts.round2(new BigDecimal("0.125"))).isEqualByComparingTo("0.13");
        assertThat(Amounts.round2(new BigDecimal("990"))).isEqualByComparingTo("990.00");
    }
}