package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocksim.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private long orderPerSecond = 5;
    private long quotePerSecond = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getOrderPerSecond() {
        return orderPerSecond;
    }

    public void setOrderPerSecond(long orderPerSecond) {
        this.orderPerSecond = orderPerSecond;
    }

    public long getQuotePerSecond() {
        return quotePerSecond;
    }

    public void setQuotePerSecond(long quotePerSecond) {
        this.quotePerSecond = quotePerSecond;
    }
}