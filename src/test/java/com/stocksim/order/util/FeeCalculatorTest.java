package com.stocksim.order.util;

import com.stocksim.common.properties.TradeProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator(new TradeProperties());

    @Test
    void 佣金最低5元() {
        assertThat(calculator.commission(new BigDecimal("1000.00"))).isEqualByComparingTo("5.00");
    }

    @Test
    void 佣金按比例() {
        assertThat(calculator.commission(new BigDecimal("100000.00"))).isEqualByComparingTo("25.00");
    }

    @Test
    void 佣金HALF_UP到分() {
        // 20020 × 0.00025 = 5.005 → 5.01
        assertThat(calculator.commission(new BigDecimal("20020"))).isEqualByComparingTo("5.01");
    }

    @Test
    void 印花税卖出() {
        assertThat(calculator.stampTax(new BigDecimal("1000.00"))).isEqualByComparingTo("0.50");
    }

    @Test
    void 印花税HALF_UP到分() {
        // 10 × 0.0005 = 0.005 → 0.01
        assertThat(calculator.stampTax(new BigDecimal("10"))).isEqualByComparingTo("0.01");
    }

    @Test
    void 卖出费用合计() {
        assertThat(calculator.sellFee(new BigDecimal("1000.00"))).isEqualByComparingTo("5.50");
    }

    @Test
    void 零股小额费用按成交金额封顶() {
        // 5.00：佣金 5 + 印花 0.00 = 5.00，封顶 5.00，入账 0
        assertThat(calculator.sellFee(new BigDecimal("5.00"))).isEqualByComparingTo("5.00");
        // 4.00：佣金 5 + 印花 0.00 = 5.00 → 封顶 4.00，余额不为负
        assertThat(calculator.sellFee(new BigDecimal("4.00"))).isEqualByComparingTo("4.00");
    }

    @Test
    void 买入预估佣金等于佣金() {
        assertThat(calculator.buyFeeEstimate(new BigDecimal("1000.00"))).isEqualByComparingTo("5.00");
        assertThat(calculator.buyFeeEstimate(new BigDecimal("100000.00"))).isEqualByComparingTo("25.00");
    }
}