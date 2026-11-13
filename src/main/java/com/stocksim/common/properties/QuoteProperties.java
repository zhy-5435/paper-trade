package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "stocksim.quote")
public class QuoteProperties {

    private long refreshIntervalMs = 3000;
    private BigDecimal walkStepRatio = new BigDecimal("0.002");

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = refreshIntervalMs;
    }

    public BigDecimal getWalkStepRatio() {
        return walkStepRatio;
    }

    public void setWalkStepRatio(BigDecimal walkStepRatio) {
        this.walkStepRatio = walkStepRatio;
    }
}