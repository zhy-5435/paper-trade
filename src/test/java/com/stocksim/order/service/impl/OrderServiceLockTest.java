package com.stocksim.order.service.impl;

import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.common.properties.TradeProperties;
import com.stocksim.order.dto.CreateOrderRequest;
import com.stocksim.order.mapper.OrderStatusLogMapper;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.util.FeeCalculator;
import com.stocksim.position.service.PositionService;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.service.QuoteService;
import com.stocksim.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceLockTest {

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private StockService stockService;
    @Mock
    private QuoteService quoteService;
    @Mock
    private AccountService accountService;
    @Mock
    private PositionService positionService;
    @Mock
    private BizNoGenerator bizNoGenerator;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LockProvider lockProvider;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;
    @Mock
    private Lock lock;

    @Test
    void 账户锁超时_下单5001且不开事务() throws InterruptedException {
        TradeProperties tradeProperties = new TradeProperties();
        OrderServiceImpl service = new OrderServiceImpl(tradeOrderMapper, stockService, quoteService,
                accountService, positionService, new FeeCalculator(tradeProperties), bizNoGenerator,
                tradeProperties, transactionTemplate, eventPublisher, lockProvider, orderStatusLogMapper);
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);
        when(stockService.getStock("600000")).thenReturn(new StockResponse("600000", "浦发银行", 1, 1,
                new BigDecimal("10.00"), new BigDecimal("0.10"), 100, 1, LocalDate.of(2020, 1, 1)));
        when(quoteService.getQuote("600000")).thenReturn(new QuoteResponse("600000", LocalDate.of(2026, 11, 16),
                new BigDecimal("10.00"), null, new BigDecimal("10.00"), null, null,
                new BigDecimal("11.00"), new BigDecimal("9.00"), 0L, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDateTime.of(2026, 11, 16, 9, 30)));
        when(accountService.getAccount(2L)).thenReturn(new AccountResponse("A2", "CNY",
                new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("100000.00"), BigDecimal.ZERO, 1));
        when(lockProvider.accountLock(2L)).thenReturn(lock);
        when(lock.tryLock(3, TimeUnit.SECONDS)).thenReturn(false);

        CreateOrderRequest request = new CreateOrderRequest("r1", "600000", 1, new BigDecimal("10.00"), 100L);

        assertThatThrownBy(() -> service.createOrder(2L, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(transactionTemplate, never()).execute(any());
    }
}