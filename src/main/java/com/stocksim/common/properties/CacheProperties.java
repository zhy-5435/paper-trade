package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocksim.cache")
public class CacheProperties {

    private long quoteTtlMs = 3000;
    private long quoteTtlJitterMs = 1000;
    private long stockTtlMs = 1800000;
    private long bloomExpectedInsertions = 10000;
    private double bloomFalseProbability = 0.01;
    private long delayedDoubleDeleteMs = 1000;

    public long getQuoteTtlMs() {
        return quoteTtlMs;
    }

    public void setQuoteTtlMs(long quoteTtlMs) {
        this.quoteTtlMs = quoteTtlMs;
    }

    public long getQuoteTtlJitterMs() {
        return quoteTtlJitterMs;
    }

    public void setQuoteTtlJitterMs(long quoteTtlJitterMs) {
        this.quoteTtlJitterMs = quoteTtlJitterMs;
    }

    public long getStockTtlMs() {
        return stockTtlMs;
    }

    public void setStockTtlMs(long stockTtlMs) {
        this.stockTtlMs = stockTtlMs;
    }

    public long getBloomExpectedInsertions() {
        return bloomExpectedInsertions;
    }

    public void setBloomExpectedInsertions(long bloomExpectedInsertions) {
        this.bloomExpectedInsertions = bloomExpectedInsertions;
    }

    public double getBloomFalseProbability() {
        return bloomFalseProbability;
    }

    public void setBloomFalseProbability(double bloomFalseProbability) {
        this.bloomFalseProbability = bloomFalseProbability;
    }

    public long getDelayedDoubleDeleteMs() {
        return delayedDoubleDeleteMs;
    }

    public void setDelayedDoubleDeleteMs(long delayedDoubleDeleteMs) {
        this.delayedDoubleDeleteMs = delayedDoubleDeleteMs;
    }
}