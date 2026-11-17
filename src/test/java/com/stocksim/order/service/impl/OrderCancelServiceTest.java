package com.stocksim.order.service.impl;

import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.lock.LocalLockProvider;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.match.book.BookOrder;
import com.stocksim.match.book.InMemoryOrderBook;
import com.stocksim.match.engine.MatchEngine;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    private static final String STOCK = "600000";
    private static final LocalDate D = LocalDate.of(2026, 11, 13);

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private DealRecordMapper dealRecordMapper;
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
    @Spy
    private LockProvider lockProvider = new LocalLockProvider();

    private InMemoryOrderBook book;
    private OrderCancelService service;

    @BeforeEach
    void setUp() {
        book = new InMemoryOrderBook();
        service = new OrderCancelService(tradeOrderMapper, dealRecordMapper, accountService,
                positionService, orderService, matchEngine, transactionTemplate, lockProvider);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        lenient().when(matchEngine.bookOf(any())).thenReturn(book);
        lenient().when(orderService.casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class), anyInt(), any()))
                .thenReturn(true);
        lenient().when(orderService.getOrder(anyLong(), any())).thenReturn(response(OrderStateMachine.CANCELED));
    }

    private OrderResponse response(int status) {
        return new OrderResponse("O1", STOCK, 1, 1, new BigDecimal("10.00"), 100L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO, status, D,
                D.atTime(15, 0), null, LocalDateTime.now());
    }

    private TradeOrder buyOrder(int status, long matched, String frozen) {
        TradeOrder o = new TradeOrder();
        o.setId(1L);
        o.setOrderNo("O1");
        o.setUserId(2L);
        o.setStockCode(STOCK);
        o.setDirection(1);
        o.setEntrustPrice(new BigDecimal("10.00"));
        o.setEntrustQty(200L);
        o.setMatchedQty(matched);
        o.setFrozenAmount(new BigDecimal(frozen));
        o.setFrozenQty(0L);
        o.setStatus(status);
        o.setTradeDate(D);
        o.setVersion(0);
        return o;
    }

    private TradeOrder sellOrder(int status, long frozenQty) {
        TradeOrder o = new TradeOrder();
        o.setId(2L);
        o.setOrderNo("O2");
        o.setUserId(2L);
        o.setStockCode(STOCK);
        o.setDirection(2);
        o.setEntrustPrice(new BigDecimal("10.00"));
        o.setEntrustQty(100L);
        o.setMatchedQty(0L);
        o.setFrozenAmount(BigDecimal.ZERO);
        o.setFrozenQty(frozenQty);
        o.setStatus(status);
        o.setTradeDate(D);
        o.setVersion(0);
        return o;
    }

    private void addBuyToBook() {
        book.add(new BookOrder(1L, "O1", 2L, STOCK, 1, new BigDecimal("10.00"), 200L, LocalDateTime.now()));
    }

    @Test
    void B2_挂单无成交撤单_状态4解冻C4零残留() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "2005.00");
        addBuyToBook();
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(dealRecordMapper.sumDealQtyByOrderNo("O1")).thenReturn(0L);

        service.cancelByUser(2L, "O1");

        verify(accountService).unfreeze(2L, new BigDecimal("2005.00"), "O1C4", "O1");
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(order), eq(OrderStateMachine.CANCELED), update.capture(), eq(2), eq(2L));
        assertThat(update.getValue().getRejectReason()).isEqualTo("用户撤单");
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(book.contains("O1")).isFalse();
        verifyNoInteractions(positionService);
    }

    @Test
    void B3_部分成交撤单_状态5解冻精确剩余() {
        TradeOrder order = buyOrder(OrderStateMachine.PARTIAL_FILLED, 100, "1002.50");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(dealRecordMapper.sumDealQtyByOrderNo("O1")).thenReturn(100L);

        service.cancelByUser(2L, "O1");

        verify(accountService).unfreeze(2L, new BigDecimal("1002.50"), "O1C4", "O1");
        verify(orderService).casTransit(eq(order), eq(OrderStateMachine.PARTIAL_CANCELED), any(TradeOrder.class), eq(2), eq(2L));
    }

    @Test
    void B4_撤已成交订单_3007() {
        TradeOrder order = buyOrder(OrderStateMachine.FILLED, 200, "0.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3007);
        verifyNoInteractions(accountService, positionService, transactionTemplate);
        verify(orderService, never()).casTransit(any(), anyInt(), any(), anyInt(), any());
    }

    @Test
    void B4_撤已撤单订单_3007() {
        TradeOrder order = buyOrder(OrderStateMachine.CANCELED, 0, "0.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3007);
    }

    @Test
    void B5_撤他人订单_1003() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "2005.00");
        order.setUserId(3L);
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(1003);
        verifyNoInteractions(accountService, transactionTemplate);
    }

    @Test
    void 订单不存在_1001() {
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O404"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(1001);
    }

    @Test
    void B6_在途记账窗口撤单_4002不动账() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "2005.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(dealRecordMapper.sumDealQtyByOrderNo("O1")).thenReturn(100L);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(4002);
        verify(accountService, never()).unfreeze(any(), any(), any(), any());
        verify(orderService, never()).casTransit(any(), anyInt(), any(), anyInt(), any());
    }

    @Test
    void 卖单撤单_解冻持仓C5() {
        TradeOrder order = sellOrder(OrderStateMachine.PENDING, 100L);
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(dealRecordMapper.sumDealQtyByOrderNo("O2")).thenReturn(0L);

        service.cancelByUser(2L, "O2");

        verify(positionService).unfreeze(2L, STOCK, 100L, "O2C5", "O2");
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(order), eq(OrderStateMachine.CANCELED), update.capture(), eq(2), eq(2L));
        assertThat(update.getValue().getFrozenQty()).isZero();
        verifyNoInteractions(accountService);
    }

    @Test
    void 撤单锁超时_4002且不开事务() throws InterruptedException {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "2005.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        Lock held = mock(Lock.class);
        when(held.tryLock(3, TimeUnit.SECONDS)).thenReturn(false);
        doReturn(held).when(lockProvider).stockLock(STOCK);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(4002);
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void 撤单锁后端故障_failFast5001() throws InterruptedException {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "2005.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        Lock held = mock(Lock.class);
        when(held.tryLock(3, TimeUnit.SECONDS)).thenThrow(new RuntimeException("redis down"));
        doReturn(held).when(lockProvider).stockLock(STOCK);

        assertThatThrownBy(() -> service.cancelByUser(2L, "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void 过期撤单成功_状态6类型3操作员空() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "1000.00");
        addBuyToBook();
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(dealRecordMapper.sumDealQtyByOrderNo("O1")).thenReturn(0L);

        boolean expired = service.expireOne("O1", STOCK);

        assertThat(expired).isTrue();
        verify(accountService).unfreeze(2L, new BigDecimal("1000.00"), "O1C4", "O1");
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(order), eq(OrderStateMachine.EXPIRED), update.capture(), eq(3), isNull());
        assertThat(update.getValue().getRejectReason()).isEqualTo("日切过期撤单");
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(book.contains("O1")).isFalse();
    }

    @Test
    void 过期撤单遇在途_跳过不失败() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, 0, "1000.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(dealRecordMapper.sumDealQtyByOrderNo("O1")).thenReturn(100L);

        boolean expired = service.expireOne("O1", STOCK);

        assertThat(expired).isFalse();
        verify(accountService, never()).unfreeze(any(), any(), any(), any());
        verify(orderService, never()).casTransit(any(), anyInt(), any(), anyInt(), any());
    }

    @Test
    void 过期撤单状态已变_跳过() {
        TradeOrder order = buyOrder(OrderStateMachine.FILLED, 200, "0.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);

        boolean expired = service.expireOne("O1", STOCK);

        assertThat(expired).isFalse();
        verifyNoInteractions(accountService, positionService);
    }

    @Test
    void 过期撤单锁超时_跳过不失败() throws InterruptedException {
        Lock held = mock(Lock.class);
        when(held.tryLock(3, TimeUnit.SECONDS)).thenReturn(false);
        doReturn(held).when(lockProvider).stockLock(STOCK);

        boolean expired = service.expireOne("O1", STOCK);

        assertThat(expired).isFalse();
        verify(transactionTemplate, never()).execute(any());
    }
}