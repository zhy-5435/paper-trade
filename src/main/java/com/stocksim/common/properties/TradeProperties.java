package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "stocksim.trade")
public class TradeProperties {

    private boolean sessionCheckEnabled = false;
    private BigDecimal commissionRate = new BigDecimal("0.00025");
    private BigDecimal commissionMin = new BigDecimal("5");
    private BigDecimal stampTaxRate = new BigDecimal("0.0005");

    public boolean isSessionCheckEnabled() {
        return sessionCheckEnabled;
    }

    public void setSessionCheckEnabled(boolean sessionCheckEnabled) {
        this.sessionCheckEnabled = sessionCheckEnabled;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getCommissionMin() {
        return commissionMin;
    }

    public void setCommissionMin(BigDecimal commissionMin) {
        this.commissionMin = commissionMin;
    }

    public BigDecimal getStampTaxRate() {
        return stampTaxRate;
    }

    public void setStampTaxRate(BigDecimal stampTaxRate) {
        this.stampTaxRate = stampTaxRate;
    }
}