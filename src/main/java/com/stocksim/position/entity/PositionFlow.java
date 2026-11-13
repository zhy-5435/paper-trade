package com.stocksim.position.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("position_flow")
public class PositionFlow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String flowNo;
    private Long userId;
    private String stockCode;
    private Integer changeType;
    private Long changeQty;
    private Long changeFrozen;
    private BigDecimal dealPrice;
    private Long totalQtyBefore;
    private Long totalQtyAfter;
    private Long frozenQtyBefore;
    private Long frozenQtyAfter;
    private BigDecimal costBefore;
    private BigDecimal costAfter;
    private String bizNo;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowNo() {
        return flowNo;
    }

    public void setFlowNo(String flowNo) {
        this.flowNo = flowNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStockCode() {
        return stockCode;
    }

    public void setStockCode(String stockCode) {
        this.stockCode = stockCode;
    }

    public Integer getChangeType() {
        return changeType;
    }

    public void setChangeType(Integer changeType) {
        this.changeType = changeType;
    }

    public Long getChangeQty() {
        return changeQty;
    }

    public void setChangeQty(Long changeQty) {
        this.changeQty = changeQty;
    }

    public Long getChangeFrozen() {
        return changeFrozen;
    }

    public void setChangeFrozen(Long changeFrozen) {
        this.changeFrozen = changeFrozen;
    }

    public BigDecimal getDealPrice() {
        return dealPrice;
    }

    public void setDealPrice(BigDecimal dealPrice) {
        this.dealPrice = dealPrice;
    }

    public Long getTotalQtyBefore() {
        return totalQtyBefore;
    }

    public void setTotalQtyBefore(Long totalQtyBefore) {
        this.totalQtyBefore = totalQtyBefore;
    }

    public Long getTotalQtyAfter() {
        return totalQtyAfter;
    }

    public void setTotalQtyAfter(Long totalQtyAfter) {
        this.totalQtyAfter = totalQtyAfter;
    }

    public Long getFrozenQtyBefore() {
        return frozenQtyBefore;
    }

    public void setFrozenQtyBefore(Long frozenQtyBefore) {
        this.frozenQtyBefore = frozenQtyBefore;
    }

    public Long getFrozenQtyAfter() {
        return frozenQtyAfter;
    }

    public void setFrozenQtyAfter(Long frozenQtyAfter) {
        this.frozenQtyAfter = frozenQtyAfter;
    }

    public BigDecimal getCostBefore() {
        return costBefore;
    }

    public void setCostBefore(BigDecimal costBefore) {
        this.costBefore = costBefore;
    }

    public BigDecimal getCostAfter() {
        return costAfter;
    }

    public void setCostAfter(BigDecimal costAfter) {
        this.costAfter = costAfter;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}