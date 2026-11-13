package com.stocksim.position.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("user_position")
public class UserPosition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String stockCode;
    private Long totalQty;
    private Long frozenQty;
    private Long todayBuyQty;
    private BigDecimal costAmount;
    private BigDecimal avgCostPrice;
    private BigDecimal latestPrice;
    private BigDecimal marketValue;
    private BigDecimal floatingPnl;
    private BigDecimal pnlRatio;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(Long totalQty) {
        this.totalQty = totalQty;
    }

    public Long getFrozenQty() {
        return frozenQty;
    }

    public void setFrozenQty(Long frozenQty) {
        this.frozenQty = frozenQty;
    }

    public Long getTodayBuyQty() {
        return todayBuyQty;
    }

    public void setTodayBuyQty(Long todayBuyQty) {
        this.todayBuyQty = todayBuyQty;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public BigDecimal getAvgCostPrice() {
        return avgCostPrice;
    }

    public void setAvgCostPrice(BigDecimal avgCostPrice) {
        this.avgCostPrice = avgCostPrice;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
        this.latestPrice = latestPrice;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    public BigDecimal getFloatingPnl() {
        return floatingPnl;
    }

    public void setFloatingPnl(BigDecimal floatingPnl) {
        this.floatingPnl = floatingPnl;
    }

    public BigDecimal getPnlRatio() {
        return pnlRatio;
    }

    public void setPnlRatio(BigDecimal pnlRatio) {
        this.pnlRatio = pnlRatio;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}