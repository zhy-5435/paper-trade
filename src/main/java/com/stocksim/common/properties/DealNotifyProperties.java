package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocksim.deal")
public class DealNotifyProperties {

    /** 成交回报实现：sync|mq（Q11）。M0–M3 仅 sync 实现就绪，默认 sync 保基线。 */
    private String notify = "sync";

    public String getNotify() {
        return notify;
    }

    public void setNotify(String notify) {
        this.notify = notify;
    }
}