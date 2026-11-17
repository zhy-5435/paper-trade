package com.stocksim.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.dto.FlowResponse;
import com.stocksim.account.dto.MoneyRequest;
import com.stocksim.account.entity.FundFlow;
import com.stocksim.account.entity.UserAccount;
import com.stocksim.account.mapper.FundFlowMapper;
import com.stocksim.account.mapper.UserAccountMapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.PageResult;
import com.stocksim.common.lock.LockProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Service
public class AccountServiceImpl implements AccountService {

    private static final int MAX_CAS_RETRY = 3;

    private final UserAccountMapper userAccountMapper;
    private final FundFlowMapper fundFlowMapper;
    private final BizNoGenerator bizNoGenerator;
    private final TransactionTemplate transactionTemplate;
    private final LockProvider lockProvider;

    public AccountServiceImpl(UserAccountMapper userAccountMapper, FundFlowMapper fundFlowMapper,
                              BizNoGenerator bizNoGenerator, TransactionTemplate transactionTemplate,
                              LockProvider lockProvider) {
        this.userAccountMapper = userAccountMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.bizNoGenerator = bizNoGenerator;
        this.transactionTemplate = transactionTemplate;
        this.lockProvider = lockProvider;
    }

    @Override
    @Transactional
    public UserAccount openAccount(Long userId) {
        UserAccount existing = findByUserId(userId);
        if (existing != null) {
            return existing;
        }
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setAccountNo(bizNoGenerator.accountNo());
        account.setCurrency("CNY");
        account.setBalance(BigDecimal.ZERO);
        account.setFrozenAmount(BigDecimal.ZERO);
        account.setTotalAsset(BigDecimal.ZERO);
        account.setStatus(1);
        try {
            userAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            return findByUserId(userId);
        }
        return account;
    }

    @Override
    public AccountResponse getAccount(Long userId) {
        UserAccount account = requireAccount(userId);
        return new AccountResponse(account.getAccountNo(), account.getCurrency(), account.getBalance(),
                account.getFrozenAmount(), available(account), account.getTotalAsset(), account.getStatus());
    }

    @Override
    public FlowResponse deposit(Long userId, MoneyRequest request) {
        return applyMoney(userId, request, true);
    }

    @Override
    public FlowResponse withdraw(Long userId, MoneyRequest request) {
        return applyMoney(userId, request, false);
    }

    @Override
    public PageResult<FlowResponse> listFlows(Long userId, long page, long size, Integer flowType) {
        Page<FundFlow> result = fundFlowMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getUserId, userId)
                        .eq(flowType != null, FundFlow::getFlowType, flowType)
                        .orderByDesc(FundFlow::getCreateTime)
                        .orderByDesc(FundFlow::getId));
        List<FlowResponse> records = result.getRecords().stream().map(this::toFlowResponse).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public void freeze(Long userId, BigDecimal amount, String flowNo, String bizNo) {
        requirePositive(amount);
        UserAccount account = requireAccount(userId);
        if (available(account).compareTo(amount) < 0) {
            throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH, "可用资金不足");
        }
        applyInternal(account, 3, BigDecimal.ZERO, amount, 1, bizNo, flowNo, "订单冻结");
    }

    @Override
    @Transactional
    public void unfreeze(Long userId, BigDecimal amount, String flowNo, String bizNo) {
        requirePositive(amount);
        applyInternal(requireAccount(userId), 4, BigDecimal.ZERO, amount.negate(), 1, bizNo, flowNo, "订单解冻");
    }

    @Override
    @Transactional
    public void deduct(Long userId, BigDecimal amount, BigDecimal unfreezeAmount, String flowNo, String bizNo) {
        requirePositive(amount);
        if (unfreezeAmount == null || unfreezeAmount.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "解冻金额非法");
        }
        applyInternal(requireAccount(userId), 5, amount.negate(), unfreezeAmount.negate(), 2, bizNo, flowNo, "买入扣款");
    }

    @Override
    @Transactional
    public void credit(Long userId, BigDecimal amount, String flowNo, String bizNo) {
        requirePositive(amount);
        applyInternal(requireAccount(userId), 6, amount, BigDecimal.ZERO, 2, bizNo, flowNo, "卖出入账");
    }

    @Override
    @Transactional
    public void fee(Long userId, BigDecimal amount, String flowNo, String bizNo) {
        requirePositive(amount);
        applyInternal(requireAccount(userId), 7, amount.negate(), BigDecimal.ZERO, 2, bizNo, flowNo, "手续费");
    }

    private FlowResponse applyMoney(Long userId, MoneyRequest request, boolean deposit) {
        FundFlow existing = findFlowByRequest(userId, request.requestId());
        if (existing != null) {
            return toFlowResponse(existing);
        }
        Lock lock = lockProvider.accountLock(userId);
        boolean acquired;
        try {
            acquired = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SYSTEM_BUSY, "账户繁忙，请重试");
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "账户繁忙，请重试");
        }
        if (!acquired) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "账户繁忙，请重试");
        }
        try {
            for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
                try {
                    // RR 隔离级别下同事务重读仍是旧快照，CAS 重试必须每次开新事务
                    return transactionTemplate.execute(status -> doApplyMoney(userId, request, deposit));
                } catch (CasConflictException e) {
                    // 换事务重试
                } catch (DuplicateKeyException e) {
                    return toFlowResponse(requireFlowByRequest(userId, request.requestId()));
                }
            }
            throw new BizException(ErrorCode.SYSTEM_BUSY, "账户并发冲突，请重试");
        } finally {
            lock.unlock();
        }
    }

    private FlowResponse doApplyMoney(Long userId, MoneyRequest request, boolean deposit) {
        FundFlow existing = findFlowByRequest(userId, request.requestId());
        if (existing != null) {
            return toFlowResponse(existing);
        }
        UserAccount account = requireAccount(userId);
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_FROZEN, "账户状态异常");
        }
        BigDecimal amount = request.amount();
        if (!deposit && available(account).compareTo(amount) < 0) {
            throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH, "可用资金不足");
        }
        BigDecimal changeBalance = deposit ? amount : amount.negate();
        casUpdate(account, account.getBalance().add(changeBalance), account.getFrozenAmount());
        FundFlow flow = buildFlow(account, deposit ? 1 : 2, changeBalance, BigDecimal.ZERO,
                4, null, request.requestId(), bizNoGenerator.flowNo(), deposit ? "入金" : "出金");
        fundFlowMapper.insert(flow);
        return toFlowResponse(flow);
    }

    private void applyInternal(UserAccount account, int flowType, BigDecimal changeBalance, BigDecimal changeFrozen,
                               int bizType, String bizNo, String flowNo, String remark) {
        casUpdate(account, account.getBalance().add(changeBalance), account.getFrozenAmount().add(changeFrozen));
        fundFlowMapper.insert(buildFlow(account, flowType, changeBalance, changeFrozen, bizType, bizNo, null, flowNo, remark));
    }

    private void casUpdate(UserAccount account, BigDecimal newBalance, BigDecimal newFrozen) {
        UserAccount update = new UserAccount();
        update.setId(account.getId());
        update.setVersion(account.getVersion());
        update.setBalance(newBalance);
        update.setFrozenAmount(newFrozen);
        if (userAccountMapper.updateById(update) == 0) {
            throw new CasConflictException();
        }
    }

    private FundFlow buildFlow(UserAccount account, int flowType, BigDecimal changeBalance, BigDecimal changeFrozen,
                               int bizType, String bizNo, String requestId, String flowNo, String remark) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo(flowNo);
        flow.setUserId(account.getUserId());
        flow.setAccountId(account.getId());
        flow.setRequestId(requestId);
        flow.setFlowType(flowType);
        flow.setChangeBalance(changeBalance);
        flow.setChangeFrozen(changeFrozen);
        flow.setBalanceBefore(account.getBalance());
        flow.setBalanceAfter(account.getBalance().add(changeBalance));
        flow.setFrozenBefore(account.getFrozenAmount());
        flow.setFrozenAfter(account.getFrozenAmount().add(changeFrozen));
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setRemark(remark);
        return flow;
    }

    private UserAccount requireAccount(Long userId) {
        UserAccount account = findByUserId(userId);
        if (account == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "账户不存在");
        }
        return account;
    }

    private UserAccount findByUserId(Long userId) {
        return userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
    }

    private FundFlow findFlowByRequest(Long userId, String requestId) {
        return fundFlowMapper.selectOne(
                new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getUserId, userId)
                        .eq(FundFlow::getRequestId, requestId));
    }

    private FundFlow requireFlowByRequest(Long userId, String requestId) {
        FundFlow flow = findFlowByRequest(userId, requestId);
        if (flow == null) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "幂等流水丢失");
        }
        return flow;
    }

    private static BigDecimal available(UserAccount account) {
        return account.getBalance().subtract(account.getFrozenAmount());
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "金额必须大于0");
        }
    }

    private FlowResponse toFlowResponse(FundFlow flow) {
        return new FlowResponse(flow.getFlowNo(), flow.getRequestId(), flow.getFlowType(),
                flow.getChangeBalance(), flow.getChangeFrozen(), flow.getBalanceBefore(), flow.getBalanceAfter(),
                flow.getFrozenBefore(), flow.getFrozenAfter(), flow.getBizType(), flow.getBizNo(),
                flow.getRemark(), flow.getCreateTime());
    }

    private static class CasConflictException extends BizException {
        CasConflictException() {
            super(ErrorCode.SYSTEM_BUSY, "账户并发冲突，请重试");
        }
    }
}