package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocksim.mq")
public class MqProperties {

    private String topic = "STOCKSIM_DEAL_BOOKING";
    private String buyTag = "BUY";
    private String sellTag = "SELL";
    private String buyConsumerGroup = "stocksim-buy-consumer";
    private String sellConsumerGroup = "stocksim-sell-consumer";
    private int maxReconsumeTimes = 5;
    private long compensateIntervalMs = 60000;
    private int compensateWindowMinutes = 10;
    private boolean testMode = false;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getBuyTag() {
        return buyTag;
    }

    public void setBuyTag(String buyTag) {
        this.buyTag = buyTag;
    }

    public String getSellTag() {
        return sellTag;
    }

    public void setSellTag(String sellTag) {
        this.sellTag = sellTag;
    }

    public String getBuyConsumerGroup() {
        return buyConsumerGroup;
    }

    public void setBuyConsumerGroup(String buyConsumerGroup) {
        this.buyConsumerGroup = buyConsumerGroup;
    }

    public String getSellConsumerGroup() {
        return sellConsumerGroup;
    }

    public void setSellConsumerGroup(String sellConsumerGroup) {
        this.sellConsumerGroup = sellConsumerGroup;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public long getCompensateIntervalMs() {
        return compensateIntervalMs;
    }

    public void setCompensateIntervalMs(long compensateIntervalMs) {
        this.compensateIntervalMs = compensateIntervalMs;
    }

    public int getCompensateWindowMinutes() {
        return compensateWindowMinutes;
    }

    public void setCompensateWindowMinutes(int compensateWindowMinutes) {
        this.compensateWindowMinutes = compensateWindowMinutes;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
}