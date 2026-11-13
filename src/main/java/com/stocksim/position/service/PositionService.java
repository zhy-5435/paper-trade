package com.stocksim.position.service;

import com.stocksim.common.PageResult;
import com.stocksim.position.dto.PositionFlowResponse;
import com.stocksim.position.dto.PositionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PositionService {

    List<PositionResponse> listPositions(Long userId);

    /** 单持仓查询（含实时市值口径），无持仓返回 null；供下单校验使用 */
    PositionResponse getPosition(Long userId, String stockCode);

    PageResult<PositionFlowResponse> listFlows(Long userId, long page, long size, String stockCode);

    /** 实时市值合计（查询时计算不落库），供账户总资产口径使用 */
    BigDecimal totalMarketValue(Long userId);

    /**
     * 内部记账（供 order/match/settlement 在调用方事务内调用）。
     * CAS 冲突抛 BizException 整体回滚，由调用方重试；流水号按 03§六.3 派生传入。
     */
    void freeze(Long userId, String stockCode, long qty, String flowNo, String bizNo);

    void unfreeze(Long userId, String stockCode, long qty, String flowNo, String bizNo);

    /** 买入成交增仓：成本加权平均（含费用）、today_buy_qty 增加（B1） */
    void addOnBuy(Long userId, String stockCode, long qty, BigDecimal dealAmount, BigDecimal fee,
                  BigDecimal dealPrice, String flowNo, String bizNo);

    /** 卖出成交减仓：同步解冻、按均价结转成本、清仓归零（S2） */
    void reduceOnSell(Long userId, String stockCode, long qty, BigDecimal dealPrice, String flowNo, String bizNo);
}