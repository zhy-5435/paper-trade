package com.stocksim.stock.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceLimitsTest {

    @Test
    void 主板10涨跌幅() {
        assertEquals(new BigDecimal("11.00"), PriceLimits.up(new BigDecimal("10.00"), new BigDecimal("0.10")));
        assertEquals(new BigDecimal("9.00"), PriceLimits.down(new BigDecimal("10.00"), new BigDecimal("0.10")));
    }

    @Test
    void ST板5涨跌幅() {
        assertEquals(new BigDecimal("5.25"), PriceLimits.up(new BigDecimal("5.00"), new BigDecimal("0.05")));
        assertEquals(new BigDecimal("4.75"), PriceLimits.down(new BigDecimal("5.00"), new BigDecimal("0.05")));
    }

    @Test
    void 涨跌停价四舍五入到分() {
        assertEquals(new BigDecimal("11.03"), PriceLimits.up(new BigDecimal("10.03"), new BigDecimal("0.10")));
        assertEquals(new BigDecimal("9.03"), PriceLimits.down(new BigDecimal("10.03"), new BigDecimal("0.10")));
    }
}