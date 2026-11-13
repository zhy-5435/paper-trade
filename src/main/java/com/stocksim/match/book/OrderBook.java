package com.stocksim.match.book;

/**
 * 盘口接口隔离实现（04§7 演进红线）：盘口始终内存，DB 即顺序日志。
 * 实现非线程安全，由 MatchEngine 的每股串行锁保护。
 */
public interface OrderBook {

    void add(BookOrder order);

    BookOrder bestBid();

    BookOrder bestAsk();

    /** 记账成功后扣减剩余数量，归零自动摘单 */
    void reduce(BookOrder order, long qty);

    /** 撤单/过期移除（M7 日切用） */
    void remove(String orderNo);

    boolean contains(String orderNo);

    int size();
}