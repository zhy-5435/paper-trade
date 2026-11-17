package com.stocksim.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocksim.deal")
public class DealNotifyProperties {

    /** 成交回报实现：sync|mq（Q11）。M4 起默认 mq；单测纯 Mockito 构造注入不受开关影响。 */
    private String notify = "mq";

    public String getNotify() {
        return notify;
    }

    public void setNotify(String notify) {
        this.notify = notify;
    }
}