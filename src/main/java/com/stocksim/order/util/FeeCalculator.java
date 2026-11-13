package com.stocksim.order.util;

import com.stocksim.common.Amounts;
import com.stocksim.common.properties.TradeProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FeeCalculator {

    private final TradeProperties tradeProperties;

    public FeeCalculator(TradeProperties tradeProperties) {
        this.tradeProperties = tradeProperties;
    }

    public BigDecimal commission(BigDecimal amount) {
        return Amounts.round2(amount.multiply(tradeProperties.getCommissionRate())
                .max(tradeProperties.getCommissionMin()));
    }

    public BigDecimal stampTax(BigDecimal amount) {
        return Amounts.round2(amount.multiply(tradeProperties.getStampTaxRate()));
    }

    /** 卖出费用合计，零股小额按成交金额封顶（01 口径，保证余额非负） */
    public BigDecimal sellFee(BigDecimal amount) {
        return Amounts.round2(commission(amount).add(stampTax(amount)).min(amount));
    }

    /** 买单预估佣金（按委托价计，成交价≤委托价故实际费用≤预估） */
    public BigDecimal buyFeeEstimate(BigDecimal amount) {
        return commission(amount);
    }
}