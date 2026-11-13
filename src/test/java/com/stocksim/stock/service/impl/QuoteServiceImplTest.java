package com.stocksim.stock.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuoteServiceImplTest {

    private static final BigDecimal UP = new BigDecimal("11.00");
    private static final BigDecimal DOWN = new BigDecimal("9.00");

    @Test
    void 随机游走超涨停被截断() {
        BigDecimal next = QuoteServiceImpl.nextPrice(new BigDecimal("10.00"), new BigDecimal("0.5"), UP, DOWN, 1);
        assertEquals(UP, next);
    }

    @Test
    void 随机游走超跌停被截断() {
        BigDecimal next = QuoteServiceImpl.nextPrice(new BigDecimal("10.00"), new BigDecimal("0.5"), UP, DOWN, -1);
        assertEquals(DOWN, next);
    }

    @Test
    void 小幅波动保留两位小数() {
        BigDecimal next = QuoteServiceImpl.nextPrice(new BigDecimal("10.00"), new BigDecimal("0.002"), UP, DOWN, 1);
        assertEquals(new BigDecimal("10.02"), next);
        BigDecimal flat = QuoteServiceImpl.nextPrice(new BigDecimal("10.00"), new BigDecimal("0.002"), UP, DOWN, 0.1);
        assertEquals(new BigDecimal("10.00"), flat);
    }

    @Test
    void 零波动价格不变() {
        BigDecimal next = QuoteServiceImpl.nextPrice(new BigDecimal("10.00"), new BigDecimal("0.002"), UP, DOWN, 0);
        assertEquals(new BigDecimal("10.00"), next);
    }

    @Test
    void 涨跌幅四位小数() {
        assertEquals(new BigDecimal("0.1000"), QuoteServiceImpl.changeRatio(new BigDecimal("11.00"), new BigDecimal("10.00")));
        assertEquals(new BigDecimal("-0.1000"), QuoteServiceImpl.changeRatio(new BigDecimal("9.00"), new BigDecimal("10.00")));
        assertEquals(new BigDecimal("0.0030"), QuoteServiceImpl.changeRatio(new BigDecimal("10.03"), new BigDecimal("10.00")));
    }

    @Test
    void 昨收为零时涨跌幅为零() {
        assertEquals(BigDecimal.ZERO, QuoteServiceImpl.changeRatio(new BigDecimal("10.00"), BigDecimal.ZERO));
    }
}