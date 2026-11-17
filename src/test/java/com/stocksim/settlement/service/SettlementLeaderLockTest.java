package com.stocksim.settlement.service;

import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.cache.RedisCacheClient;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.match.engine.MatchEngine;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.position.mapper.UserPositionMapper;
import com.stocksim.position.service.PositionService;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementLeaderLockTest {

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private StockQuoteMapper stockQuoteMapper;
    @Mock
    private StockInfoMapper stockInfoMapper;
    @Mock
    private UserPositionMapper userPositionMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private PositionService positionService;
    @Mock
    private OrderService orderService;
    @Mock
    private MatchEngine matchEngine;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RedisCacheClient cacheClient;
    @Mock
    private LockProvider lockProvider;
    @Mock
    private Lock leader;

    @Test
    void 选主锁占用_拒绝日切5001() throws InterruptedException {
        when(lockProvider.settlementLeaderLock()).thenReturn(leader);
        when(leader.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);
        SettlementService service = new SettlementService(tradeOrderMapper, stockQuoteMapper, stockInfoMapper,
                userPositionMapper, accountService, positionService, orderService, matchEngine,
                transactionTemplate, cacheClient, lockProvider);

        assertThatThrownBy(service::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verifyNoInteractions(stockQuoteMapper, tradeOrderMapper, cacheClient);
    }
}