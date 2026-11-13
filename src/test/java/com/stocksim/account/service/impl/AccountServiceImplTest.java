package com.stocksim.account.service.impl;

import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.dto.FlowResponse;
import com.stocksim.account.dto.MoneyRequest;
import com.stocksim.account.entity.FundFlow;
import com.stocksim.account.entity.UserAccount;
import com.stocksim.account.mapper.FundFlowMapper;
import com.stocksim.account.mapper.UserAccountMapper;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;
    @Mock
    private BizNoGenerator bizNoGenerator;
    @Mock
    private TransactionTemplate transactionTemplate;
    @InjectMocks
    private AccountServiceImpl accountService;

    private UserAccount account(String balance, String frozen) {
        UserAccount a = new UserAccount();
        a.setId(100L);
        a.setUserId(2L);
        a.setAccountNo("A0000000000000000002");
        a.setCurrency("CNY");
        a.setBalance(new BigDecimal(balance));
        a.setFrozenAmount(new BigDecimal(frozen));
        a.setTotalAsset(BigDecimal.ZERO);
        a.setStatus(1);
        a.setVersion(0);
        return a;
    }

    private void txRunsCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    private FundFlow existingDepositFlow() {
        FundFlow flow = new FundFlow();
        flow.setFlowNo("F999");
        flow.setUserId(2L);
        flow.setAccountId(100L);
        flow.setRequestId("r1");
        flow.setFlowType(1);
        flow.setChangeBalance(new BigDecimal("100.00"));
        flow.setChangeFrozen(BigDecimal.ZERO);
        flow.setBalanceBefore(BigDecimal.ZERO);
        flow.setBalanceAfter(new BigDecimal("100.00"));
        flow.setFrozenBefore(BigDecimal.ZERO);
        flow.setFrozenAfter(BigDecimal.ZERO);
        flow.setBizType(4);
        return flow;
    }

    @Test
    void 入金成功_流水前后余额勾稽() {
        txRunsCallback();
        when(userAccountMapper.selectOne(any())).thenReturn(account("0.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);
        when(bizNoGenerator.flowNo()).thenReturn("F1");

        FlowResponse resp = accountService.deposit(2L, new MoneyRequest("r1", new BigDecimal("100.00")));

        assertThat(resp.flowType()).isEqualTo(1);
        assertThat(resp.changeBalance()).isEqualByComparingTo("100.00");
        assertThat(resp.balanceBefore()).isEqualByComparingTo("0.00");
        assertThat(resp.balanceAfter()).isEqualByComparingTo("100.00");
        assertThat(resp.requestId()).isEqualTo("r1");

        ArgumentCaptor<UserAccount> updateCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getBalance()).isEqualByComparingTo("100.00");
        assertThat(updateCaptor.getValue().getVersion()).isEqualTo(0);

        ArgumentCaptor<FundFlow> flowCaptor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(flowCaptor.getValue().getBizType()).isEqualTo(4);
    }

    @Test
    void 重复requestId_返回原流水不动账() {
        when(fundFlowMapper.selectOne(any())).thenReturn(existingDepositFlow());

        FlowResponse resp = accountService.deposit(2L, new MoneyRequest("r1", new BigDecimal("100.00")));

        assertThat(resp.flowNo()).isEqualTo("F999");
        assertThat(resp.balanceAfter()).isEqualByComparingTo("100.00");
        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    void 出金成功() {
        txRunsCallback();
        when(userAccountMapper.selectOne(any())).thenReturn(account("100.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);
        when(bizNoGenerator.flowNo()).thenReturn("F2");

        FlowResponse resp = accountService.withdraw(2L, new MoneyRequest("w1", new BigDecimal("30.00")));

        assertThat(resp.flowType()).isEqualTo(2);
        assertThat(resp.changeBalance()).isEqualByComparingTo("-30.00");
        assertThat(resp.balanceAfter()).isEqualByComparingTo("70.00");
    }

    @Test
    void 出金超可用余额_2001() {
        txRunsCallback();
        when(userAccountMapper.selectOne(any())).thenReturn(account("100.00", "60.00"));

        assertThatThrownBy(() -> accountService.withdraw(2L, new MoneyRequest("w1", new BigDecimal("50.00"))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2001);
        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
    }

    @Test
    void 账户冻结_入金拒绝2002() {
        txRunsCallback();
        UserAccount frozen = account("0.00", "0.00");
        frozen.setStatus(0);
        when(userAccountMapper.selectOne(any())).thenReturn(frozen);

        assertThatThrownBy(() -> accountService.deposit(2L, new MoneyRequest("r1", new BigDecimal("100.00"))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2002);
    }

    @Test
    void CAS冲突_换事务重试后成功() {
        txRunsCallback();
        when(userAccountMapper.selectOne(any())).thenReturn(account("0.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(0, 1);
        when(bizNoGenerator.flowNo()).thenReturn("F1");

        FlowResponse resp = accountService.deposit(2L, new MoneyRequest("r1", new BigDecimal("100.00")));

        assertThat(resp.balanceAfter()).isEqualByComparingTo("100.00");
        verify(userAccountMapper, times(2)).updateById(any(UserAccount.class));
        verify(fundFlowMapper, times(1)).insert(any(FundFlow.class));
    }

    @Test
    void CAS冲突三次_抛5001() {
        txRunsCallback();
        when(userAccountMapper.selectOne(any())).thenReturn(account("0.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(0);

        assertThatThrownBy(() -> accountService.deposit(2L, new MoneyRequest("r1", new BigDecimal("100.00"))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(userAccountMapper, times(3)).updateById(any(UserAccount.class));
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    void 并发重复提交_唯一键冲突回滚后返回原流水() {
        txRunsCallback();
        when(userAccountMapper.selectOne(any())).thenReturn(account("0.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);
        when(bizNoGenerator.flowNo()).thenReturn("F1");
        when(fundFlowMapper.insert(any(FundFlow.class))).thenThrow(new DuplicateKeyException("uk_user_request"));
        when(fundFlowMapper.selectOne(any())).thenReturn(null, null, existingDepositFlow());

        FlowResponse resp = accountService.deposit(2L, new MoneyRequest("r1", new BigDecimal("100.00")));

        assertThat(resp.flowNo()).isEqualTo("F999");
    }

    @Test
    void 冻结_余额不变冻结增加() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("1000.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);

        accountService.freeze(2L, new BigDecimal("200.00"), "O1F3", "O1");

        ArgumentCaptor<UserAccount> updateCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getBalance()).isEqualByComparingTo("1000.00");
        assertThat(updateCaptor.getValue().getFrozenAmount()).isEqualByComparingTo("200.00");

        ArgumentCaptor<FundFlow> flowCaptor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        FundFlow flow = flowCaptor.getValue();
        assertThat(flow.getFlowType()).isEqualTo(3);
        assertThat(flow.getChangeBalance()).isEqualByComparingTo("0.00");
        assertThat(flow.getChangeFrozen()).isEqualByComparingTo("200.00");
        assertThat(flow.getFrozenAfter()).isEqualByComparingTo("200.00");
        assertThat(flow.getBizType()).isEqualTo(1);
        assertThat(flow.getRequestId()).isNull();
        assertThat(flow.getFlowNo()).isEqualTo("O1F3");
    }

    @Test
    void 冻结超可用_2001() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("100.00", "60.00"));

        assertThatThrownBy(() -> accountService.freeze(2L, new BigDecimal("50.00"), "O1F3", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2001);
    }

    @Test
    void 解冻_冻结减少() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("1000.00", "200.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);

        accountService.unfreeze(2L, new BigDecimal("200.00"), "O1C4", "O1");

        ArgumentCaptor<FundFlow> flowCaptor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getFlowType()).isEqualTo(4);
        assertThat(flowCaptor.getValue().getChangeFrozen()).isEqualByComparingTo("-200.00");
        assertThat(flowCaptor.getValue().getFrozenAfter()).isEqualByComparingTo("0.00");
    }

    @Test
    void 买入扣款_扣余额同时按比例解冻() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("100000.00", "2005.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);

        accountService.deduct(2L, new BigDecimal("980.00"), new BigDecimal("1002.50"), "D1B5", "D1");

        ArgumentCaptor<UserAccount> updateCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getBalance()).isEqualByComparingTo("99020.00");
        assertThat(updateCaptor.getValue().getFrozenAmount()).isEqualByComparingTo("1002.50");

        ArgumentCaptor<FundFlow> flowCaptor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        FundFlow flow = flowCaptor.getValue();
        assertThat(flow.getFlowType()).isEqualTo(5);
        assertThat(flow.getChangeBalance()).isEqualByComparingTo("-980.00");
        assertThat(flow.getChangeFrozen()).isEqualByComparingTo("-1002.50");
        assertThat(flow.getBizType()).isEqualTo(2);
    }

    @Test
    void 卖出入账() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("0.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);

        accountService.credit(2L, new BigDecimal("984.50"), "D1S6", "D1");

        ArgumentCaptor<FundFlow> flowCaptor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getFlowType()).isEqualTo(6);
        assertThat(flowCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("984.50");
    }

    @Test
    void 手续费独立流水() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("1000.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);

        accountService.fee(2L, new BigDecimal("5.00"), "D1B7", "D1");

        ArgumentCaptor<FundFlow> flowCaptor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getFlowType()).isEqualTo(7);
        assertThat(flowCaptor.getValue().getChangeBalance()).isEqualByComparingTo("-5.00");
        assertThat(flowCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("995.00");
    }

    @Test
    void 内部记账CAS冲突_单次即抛不重试() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("1000.00", "0.00"));
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(0);

        assertThatThrownBy(() -> accountService.freeze(2L, new BigDecimal("200.00"), "O1F3", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(userAccountMapper, times(1)).updateById(any(UserAccount.class));
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    void 非法金额拒绝() {
        assertThatThrownBy(() -> accountService.freeze(2L, BigDecimal.ZERO, "O1F3", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(1001);
    }

    @Test
    void 账户查询_可用等于余额减冻结() {
        when(userAccountMapper.selectOne(any())).thenReturn(account("100.00", "30.00"));

        AccountResponse resp = accountService.getAccount(2L);

        assertThat(resp.availableAmount()).isEqualByComparingTo("70.00");
        assertThat(resp.balance()).isEqualByComparingTo("100.00");
        assertThat(resp.frozenAmount()).isEqualByComparingTo("30.00");
    }
}