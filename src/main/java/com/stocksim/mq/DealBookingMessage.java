package com.stocksim.mq;

import com.stocksim.match.engine.DealContext;

import java.math.BigDecimal;

/**
 * 成交记账消息体（R2规划 Q4/Q5）。普通 POJO 保证 Jackson 可靠序列化/反序列化
 * （rocketmq-spring 转换器仅注册 JavaTimeModule，final record 反序列化不可靠）。
 * keys=dealNo 供事务回查；tag BUY/SELL 区分两侧；order_no 供补偿补发顺序分片。
 */
public class DealBookingMessage {

    private String dealNo;
    private long dealSeq;
    private String stockCode;
    private BigDecimal dealPrice;
    private long dealQty;
    private String buyerOrderNo;
    private String sellerOrderNo;
    private Long buyerUserId;
    private Long sellerUserId;

    public DealBookingMessage() {
    }

    public static DealBookingMessage from(DealContext ctx) {
        DealBookingMessage m = new DealBookingMessage();
        m.dealNo = ctx.dealNo();
        m.dealSeq = ctx.dealSeq();
        m.stockCode = ctx.stockCode();
        m.dealPrice = ctx.dealPrice();
        m.dealQty = ctx.dealQty();
        m.buyerOrderNo = ctx.buyerOrderNo();
        m.sellerOrderNo = ctx.sellerOrderNo();
        m.buyerUserId = ctx.buyerUserId();
        m.sellerUserId = ctx.sellerUserId();
        return m;
    }

    public DealContext toDealContext() {
        return new DealContext(dealNo, dealSeq, stockCode, dealPrice, dealQty,
                buyerOrderNo, sellerOrderNo, buyerUserId, sellerUserId);
    }

    public String getDealNo() {
        return dealNo;
    }

    public void setDealNo(String dealNo) {
        this.dealNo = dealNo;
    }

    public long getDealSeq() {
        return dealSeq;
    }

    public void setDealSeq(long dealSeq) {
        this.dealSeq = dealSeq;
    }

    public String getStockCode() {
        return stockCode;
    }

    public void setStockCode(String stockCode) {
        this.stockCode = stockCode;
    }

    public BigDecimal getDealPrice() {
        return dealPrice;
    }

    public void setDealPrice(BigDecimal dealPrice) {
        this.dealPrice = dealPrice;
    }

    public long getDealQty() {
        return dealQty;
    }

    public void setDealQty(long dealQty) {
        this.dealQty = dealQty;
    }

    public String getBuyerOrderNo() {
        return buyerOrderNo;
    }

    public void setBuyerOrderNo(String buyerOrderNo) {
        this.buyerOrderNo = buyerOrderNo;
    }

    public String getSellerOrderNo() {
        return sellerOrderNo;
    }

    public void setSellerOrderNo(String sellerOrderNo) {
        this.sellerOrderNo = sellerOrderNo;
    }

    public Long getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(Long buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public Long getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(Long sellerUserId) {
        this.sellerUserId = sellerUserId;
    }
}