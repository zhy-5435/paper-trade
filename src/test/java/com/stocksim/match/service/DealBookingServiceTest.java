package com.stocksim.match.service;

import com.stocksim.account.mapper.FundFlowMapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.properties.TradeProperties;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealOrderDaily;
import com.stocksim.match.mapper.DealOrderDailyMapper;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.FeeCalculator;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealBookingServiceTest {

    private static final String STOCK = "600000";
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 17);

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private DealOrderDailyMapper dealOrderDailyMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private AccountService accountService;
    @Mock
    private PositionService positionService;

    private DealBookingService service;

    @BeforeEach
    void setUp() {
        service = new DealBookingService(tradeOrderMapper, dealOrderDailyMapper, fundFlowMapper,
                orderService, accountService, positionService, new FeeCalculator(new TradeProperties()));
        lenient().when(fundFlowMapper.selectCount(any())).thenReturn(0L);
        lenient().when(orderService.casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class), anyInt(), any()))
                .thenReturn(true);
    }

    private TradeOrder buyOrder(long entrust, long matched, String frozen) {
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setOrderNo("B1");
        order.setUserId(1L);
        order.setStockCode(STOCK);
        order.setDirection(1);
        order.setEntrustPrice(new BigDecimal("10.10"));
        order.setEntrustQty(entrust);
        order.setMatchedQty(matched);
        order.setMatchedAmount(BigDecimal.ZERO);
        order.setFrozenAmount(new BigDecimal(frozen));
        order.setFrozenQty(0L);
        order.setFeeAmount(BigDecimal.ZERO);
        order.setStatus(OrderStateMachine.PENDING);
        order.setTradeDate(TRADE_DATE);
        order.setVersion(0);
        return order;
    }

    private TradeOrder sellOrder(long entrust, long matched, long frozenQty) {
        TradeOrder order = new TradeOrder();
        order.setId(2L);
        order.setOrderNo("S1");
        order.setUserId(2L);
        order.setStockCode(STOCK);
        order.setDirection(2);
        order.setEntrustPrice(new BigDecimal("9.90"));
        order.setEntrustQty(entrust);
        order.setMatchedQty(matched);
        order.setMatchedAmount(BigDecimal.ZERO);
        order.setFrozenAmount(BigDecimal.ZERO);
        order.setFrozenQty(frozenQty);
        order.setFeeAmount(BigDecimal.ZERO);
        order.setStatus(OrderStateMachine.PENDING);
        order.setTradeDate(TRADE_DATE);
        order.setVersion(0);
        return order;
    }

    private DealContext ctx(String price, long qty) {
        return new DealContext("D1", 1L, STOCK, new BigDecimal(price), qty, "B1", "S1", 1L, 2L);
    }

    @Test
    void consumeBuyAlreadyBookedAcksWithoutBooking() {
        // Q5 幂等前置：B5 流水存在 → 直接 ACK，不触碰订单/账户/持仓（终态重放不误入死信）
        when(fundFlowMapper.selectCount(any())).thenReturn(1L);
        service.consumeBuy(ctx("9.90", 100));
        verifyNoInteractions(tradeOrderMapper, accountService, positionService, orderService, dealOrderDailyMapper);
    }

    @Test
    void consumeSellAlreadyBookedAcksWithoutBooking() {
        when(fundFlowMapper.selectCount(any())).thenReturn(1L);
        service.consumeSell(ctx("9.90", 100));
        verifyNoInteractions(tradeOrderMapper, accountService, positionService, orderService, dealOrderDailyMapper);
    }

    @Test
    void consumeBuyTerminalOrderNotBookedThrowsConflict() {
        // 幂等前置漏网（无流水）+ 终态订单 → requireOpenOrder 防御抛 MATCH_CONFLICT
        TradeOrder filled = buyOrder(100, 100, "0.00");
        filled.setStatus(OrderStateMachine.FILLED);
        when(tradeOrderMapper.selectOne(any())).thenReturn(filled);
        assertThatThrownBy(() -> service.consumeBuy(ctx("9.90", 100)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
        verifyNoInteractions(accountService, positionService);
    }

    @Test
    void consumeBuyFullFillNumeric() {
        // §6.3 买方：成交 @9.90×100=990，扣款 990 + 佣金 5，解冻全部 1015，持仓 100
        TradeOrder buy = buyOrder(100, 0, "1015.00");
        when(tradeOrderMapper.selectOne(any())).thenReturn(buy);
        service.consumeBuy(ctx("9.90", 100));

        verify(accountService).deduct(eq(1L), eq(new BigDecimal("990.00")), eq(new BigDecimal("1015.00")),
                eq("D1B5"), eq("D1"));
        verify(accountService).fee(eq(1L), eq(new BigDecimal("5.00")), eq("D1B7"), eq("D1"));
        verify(positionService).addOnBuy(eq(1L), eq(STOCK), eq(100L), eq(new BigDecimal("990.00")),
                eq(new BigDecimal("5.00")), eq(new BigDecimal("9.90")), eq("D1B1"), eq("D1"));
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(buy), eq(OrderStateMachine.FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getMatchedQty()).isEqualTo(100);
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void consumeSellFullFillNumeric() {
        // §6.3 卖方：入 990 −（佣金 5 + 印花税 0.50）= 984.50，减仓解冻 100
        TradeOrder sell = sellOrder(100, 0, 100);
        when(tradeOrderMapper.selectOne(any())).thenReturn(sell);
        service.consumeSell(ctx("9.90", 100));

        verify(positionService).reduceOnSell(eq(2L), eq(STOCK), eq(100L), eq(new BigDecimal("9.90")),
                eq("D1S2"), eq("D1"));
        verify(accountService).credit(eq(2L), eq(new BigDecimal("990.00")), eq("D1S6"), eq("D1"));
        verify(accountService).fee(eq(2L), eq(new BigDecimal("5.50")), eq("D1S7"), eq("D1"));
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(sell), eq(OrderStateMachine.FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getFrozenQty()).isZero();
    }

    @Test
    void bookBuyPartialUnfreezesProportionally() {
        // §6.2 买 200@10.00 冻结 2005，部分成交 100@9.80 → 解冻 2005×100/200=1002.50
        TradeOrder buy = buyOrder(200, 0, "2005.00");
        service.bookBuy(ctx("9.80", 100), buy);
        verify(accountService).deduct(eq(1L), eq(new BigDecimal("980.00")), eq(new BigDecimal("1002.50")),
                eq("D1B5"), eq("D1"));
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(buy), eq(OrderStateMachine.PARTIAL_FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("1002.50");
    }

    @Test
    void bookBuyLastFillUnfreezesExactRemainder() {
        TradeOrder buy = buyOrder(200, 100, "1002.50");
        service.bookBuy(ctx("9.80", 100), buy);
        verify(accountService).deduct(eq(1L), eq(new BigDecimal("980.00")), eq(new BigDecimal("1002.50")),
                eq("D1B5"), eq("D1"));
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(buy), eq(OrderStateMachine.FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void bookSellOddLotFeeCappedAtAmount() {
        // §6.2 卖 1 股 @5.00：佣金 5 + 印花税 0.00 按成交金额封顶 5.00，入账 0.00
        TradeOrder sell = sellOrder(1, 0, 1);
        service.bookSell(ctx("5.00", 1), sell);
        verify(accountService).credit(eq(2L), eq(new BigDecimal("5.00")), eq("D1S6"), eq("D1"));
        verify(accountService).fee(eq(2L), eq(new BigDecimal("5.00")), eq("D1S7"), eq("D1"));
    }

    @Test
    void bookBuyCasConflictThrowsMatchConflict() {
        TradeOrder buy = buyOrder(100, 0, "1015.00");
        when(orderService.casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class), anyInt(), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.bookBuy(ctx("9.90", 100), buy))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
    }

    @Test
    void bookBuyInsufficientRemainThrowsMatchConflict() {
        TradeOrder buy = buyOrder(100, 0, "1015.00");
        assertThatThrownBy(() -> service.bookBuy(ctx("9.90", 200), buy))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
        verifyNoInteractions(accountService, positionService);
    }

    @Test
    void bookBuyUpsertsDaily() {
        TradeOrder buy = buyOrder(100, 0, "1015.00");
        service.bookBuy(ctx("9.90", 100), buy);
        ArgumentCaptor<DealOrderDaily> daily = ArgumentCaptor.forClass(DealOrderDaily.class);
        verify(dealOrderDailyMapper).insert(daily.capture());
        assertThat(daily.getValue().getUserId()).isEqualTo(1L);
        assertThat(daily.getValue().getDealQty()).isEqualTo(100);
        assertThat(daily.getValue().getDealAmount()).isEqualByComparingTo("990.00");
        assertThat(daily.getValue().getFeeAmount()).isEqualByComparingTo("5.00");
        assertThat(daily.getValue().getTradeDate()).isEqualTo(TRADE_DATE);
    }
}