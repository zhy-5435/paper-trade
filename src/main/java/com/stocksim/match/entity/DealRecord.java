package com.stocksim.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("deal_record")
public class DealRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String dealNo;
    private String stockCode;
    private BigDecimal dealPrice;
    private Long dealQty;
    private BigDecimal dealAmount;
    private String buyerOrderNo;
    private String sellerOrderNo;
    private Long buyerUserId;
    private Long sellerUserId;
    private Long dealSeq;
    private LocalDateTime dealTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDealNo() {
        return dealNo;
    }

    public void setDealNo(String dealNo) {
        this.dealNo = dealNo;
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

    public Long getDealQty() {
        return dealQty;
    }

    public void setDealQty(Long dealQty) {
        this.dealQty = dealQty;
    }

    public BigDecimal getDealAmount() {
        return dealAmount;
    }

    public void setDealAmount(BigDecimal dealAmount) {
        this.dealAmount = dealAmount;
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

    public Long getDealSeq() {
        return dealSeq;
    }

    public void setDealSeq(Long dealSeq) {
        this.dealSeq = dealSeq;
    }

    public LocalDateTime getDealTime() {
        return dealTime;
    }

    public void setDealTime(LocalDateTime dealTime) {
        this.dealTime = dealTime;
    }
}