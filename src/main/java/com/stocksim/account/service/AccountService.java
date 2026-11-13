package com.stocksim.account.service;

import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.dto.FlowResponse;
import com.stocksim.account.dto.MoneyRequest;
import com.stocksim.account.entity.UserAccount;
import com.stocksim.common.PageResult;

import java.math.BigDecimal;

public interface AccountService {

    UserAccount openAccount(Long userId);

    AccountResponse getAccount(Long userId);

    FlowResponse deposit(Long userId, MoneyRequest request);

    FlowResponse withdraw(Long userId, MoneyRequest request);

    PageResult<FlowResponse> listFlows(Long userId, long page, long size, Integer flowType);

    /**
     * 内部记账（供 order/match/settlement 在调用方事务内调用）。
     * CAS 冲突抛 BizException 整体回滚，由调用方重试；流水号按 03§六.3 派生传入。
     */
    void freeze(Long userId, BigDecimal amount, String flowNo, String bizNo);

    void unfreeze(Long userId, BigDecimal amount, String flowNo, String bizNo);

    /** 买入扣款：余额扣成交金额，同时按比例解冻（B5 复合流水） */
    void deduct(Long userId, BigDecimal amount, BigDecimal unfreezeAmount, String flowNo, String bizNo);

    void credit(Long userId, BigDecimal amount, String flowNo, String bizNo);

    void fee(Long userId, BigDecimal amount, String flowNo, String bizNo);
}