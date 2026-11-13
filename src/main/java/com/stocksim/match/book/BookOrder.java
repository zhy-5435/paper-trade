package com.stocksim.match.book;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 盘口挂单条目：remainQty 只在记账事务提交成功后由引擎扣减 */
public class BookOrder {

    private final long orderId;
    private final String orderNo;
    private final Long userId;
    private final String stockCode;
    private final int direction;
    private final BigDecimal price;
    private final LocalDateTime createTime;
    private long remainQty;

    public BookOrder(long orderId, String orderNo, Long userId, String stockCode, int direction,
                     BigDecimal price, long remainQty, LocalDateTime createTime) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.stockCode = stockCode;
        this.direction = direction;
        this.price = price;
        this.remainQty = remainQty;
        this.createTime = createTime;
    }

    public long getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStockCode() {
        return stockCode;
    }

    public int getDirection() {
        return direction;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public long getRemainQty() {
        return remainQty;
    }

    public void setRemainQty(long remainQty) {
        this.remainQty = remainQty;
    }
}