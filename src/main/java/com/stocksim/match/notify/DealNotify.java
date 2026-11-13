package com.stocksim.match.notify;

import com.stocksim.match.engine.DealContext;

/** 成交回报接口：第一轮同步直调记账，正式版经 mq 异步（04§7） */
public interface DealNotify {

    void onDeal(DealContext deal);
}