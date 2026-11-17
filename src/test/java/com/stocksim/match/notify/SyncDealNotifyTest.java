package com.stocksim.match.notify;

import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.properties.TradeProperties;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealOrderDaily;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealOrderDailyMapper;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.FeeCalculator;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.service.PositionService;
import com.stocksim.stock.service.QuoteService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncDealNotifyTest {

    private static final String STOCK = "600000";
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 13);

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private DealRecordMapper dealRecordMapper;
    @Mock
    private DealOrderDailyMapper dealOrderDailyMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private AccountService accountService;
    @Mock
    private PositionService positionService;
    @Mock
    private QuoteService quoteService;

    private SyncDealNotify notify;

    @BeforeEach
    void setUp() {
        FeeCalculator feeCalculator = new FeeCalculator(new TradeProperties());
        notify = new SyncDealNotify(tradeOrderMapper, dealRecordMapper, dealOrderDailyMapper,
                orderService, accountService, positionService, quoteService, feeCalculator);
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

    private void orders(TradeOrder buy, TradeOrder sell) {
        when(tradeOrderMapper.selectOne(any())).thenReturn(buy, sell);
    }

    @Test
    void fullFillBuySideAccounting() {
        // §6.4 买方：成交 @9.90×100=990，佣金 5，扣款 990+佣金 5=995
        TradeOrder buy = buyOrder(100, 0, "1015.00");
        orders(buy, sellOrder(100, 0, 100));
        notify.onDeal(ctx("9.90", 100));

        ArgumentCaptor<DealRecord> record = ArgumentCaptor.forClass(DealRecord.class);
        verify(dealRecordMapper).insert(record.capture());
        assertThat(record.getValue().getDealNo()).isEqualTo("D1");
        assertThat(record.getValue().getDealPrice()).isEqualByComparingTo("9.90");
        assertThat(record.getValue().getDealQty()).isEqualTo(100);
        assertThat(record.getValue().getDealAmount()).isEqualByComparingTo("990.00");
        assertThat(record.getValue().getDealSeq()).isEqualTo(1);
        assertThat(record.getValue().getBuyerUserId()).isEqualTo(1L);
        assertThat(record.getValue().getSellerUserId()).isEqualTo(2L);

        verify(accountService).deduct(eq(1L), eq(new BigDecimal("990.00")), eq(new BigDecimal("1015.00")),
                eq("D1B5"), eq("D1"));
        verify(accountService).fee(eq(1L), eq(new BigDecimal("5.00")), eq("D1B7"), eq("D1"));
        verify(positionService).addOnBuy(eq(1L), eq(STOCK), eq(100L), eq(new BigDecimal("990.00")),
                eq(new BigDecimal("5.00")), eq(new BigDecimal("9.90")), eq("D1B1"), eq("D1"));

        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(buy), eq(OrderStateMachine.FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getMatchedQty()).isEqualTo(100);
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("0.00");
        verify(quoteService).accumulateDeal(STOCK, 100, new BigDecimal("990.00"));
    }

    @Test
    void fullFillSellSideAccounting() {
        // §6.4 卖方：入 990 −（佣金 5 + 印花税 0.50）= 984.50
        orders(buyOrder(100, 0, "1015.00"), sellOrder(100, 0, 100));
        notify.onDeal(ctx("9.90", 100));

        verify(positionService).reduceOnSell(eq(2L), eq(STOCK), eq(100L), eq(new BigDecimal("9.90")),
                eq("D1S2"), eq("D1"));
        verify(accountService).credit(eq(2L), eq(new BigDecimal("990.00")), eq("D1S6"), eq("D1"));
        verify(accountService).fee(eq(2L), eq(new BigDecimal("5.50")), eq("D1S7"), eq("D1"));
    }

    @Test
    void oddLotSellFeeCappedAtAmount() {
        // §6.2 卖 1 股 @5.00：佣金 5 + 印花税 0.00，按成交金额封顶 5.00，入账 0.00
        orders(buyOrder(1, 0, "5.00"), sellOrder(1, 0, 1));
        notify.onDeal(ctx("5.00", 1));

        verify(accountService).credit(eq(2L), eq(new BigDecimal("5.00")), eq("D1S6"), eq("D1"));
        verify(accountService).fee(eq(2L), eq(new BigDecimal("5.00")), eq("D1S7"), eq("D1"));
    }

    @Test
    void partialFillUnfreezesProportionally() {
        // §6.3 买 200@10.00 冻结 2005，部分成交 100@9.80 → 解冻 2005×100/200=1002.50
        TradeOrder buy = buyOrder(200, 0, "2005.00");
        orders(buy, sellOrder(100, 0, 100));
        notify.onDeal(ctx("9.80", 100));

        verify(accountService).deduct(eq(1L), eq(new BigDecimal("980.00")), eq(new BigDecimal("1002.50")),
                eq("D1B5"), eq("D1"));
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(buy), eq(OrderStateMachine.PARTIAL_FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getMatchedQty()).isEqualTo(100);
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("1002.50");
    }

    @Test
    void lastFillUnfreezesExactRemainder() {
        // §6.3 剩余 100 股全部成交 → 解冻剩余精确值 1002.50，冻结零残留
        TradeOrder buy = buyOrder(200, 100, "1002.50");
        orders(buy, sellOrder(100, 0, 100));
        notify.onDeal(ctx("9.80", 100));

        verify(accountService).deduct(eq(1L), eq(new BigDecimal("980.00")), eq(new BigDecimal("1002.50")),
                eq("D1B5"), eq("D1"));
        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(eq(buy), eq(OrderStateMachine.FILLED), update.capture(), eq(1), isNull());
        assertThat(update.getValue().getFrozenAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void dailySummaryInsertsBothSides() {
        orders(buyOrder(100, 0, "1015.00"), sellOrder(100, 0, 100));
        notify.onDeal(ctx("9.90", 100));

        ArgumentCaptor<DealOrderDaily> daily = ArgumentCaptor.forClass(DealOrderDaily.class);
        verify(dealOrderDailyMapper, org.mockito.Mockito.times(2)).insert(daily.capture());
        DealOrderDaily buyDaily = daily.getAllValues().get(0);
        DealOrderDaily sellDaily = daily.getAllValues().get(1);
        assertThat(buyDaily.getUserId()).isEqualTo(1L);
        assertThat(buyDaily.getDealQty()).isEqualTo(100);
        assertThat(buyDaily.getDealAmount()).isEqualByComparingTo("990.00");
        assertThat(buyDaily.getFeeAmount()).isEqualByComparingTo("5.00");
        assertThat(buyDaily.getTradeDate()).isEqualTo(TRADE_DATE);
        assertThat(sellDaily.getUserId()).isEqualTo(2L);
        assertThat(sellDaily.getFeeAmount()).isEqualByComparingTo("5.50");
    }

    @Test
    void dailySummaryAccumulatesOnExisting() {
        orders(buyOrder(100, 0, "1015.00"), sellOrder(100, 0, 100));
        DealOrderDaily existing = new DealOrderDaily();
        existing.setId(9L);
        existing.setUserId(1L);
        existing.setTradeDate(TRADE_DATE);
        existing.setOrderNo("B1");
        existing.setDealQty(50L);
        existing.setDealAmount(new BigDecimal("490.00"));
        existing.setFeeAmount(new BigDecimal("2.50"));
        when(dealOrderDailyMapper.selectOne(any())).thenReturn(existing, (DealOrderDaily) null);
        notify.onDeal(ctx("9.90", 100));

        ArgumentCaptor<DealOrderDaily> update = ArgumentCaptor.forClass(DealOrderDaily.class);
        verify(dealOrderDailyMapper).updateById(update.capture());
        assertThat(update.getValue().getId()).isEqualTo(9L);
        assertThat(update.getValue().getDealQty()).isEqualTo(150);
        assertThat(update.getValue().getDealAmount()).isEqualByComparingTo("1480.00");
        assertThat(update.getValue().getFeeAmount()).isEqualByComparingTo("7.50");
    }

    @Test
    void casTransitFailureThrowsMatchConflict() {
        orders(buyOrder(100, 0, "1015.00"), sellOrder(100, 0, 100));
        when(orderService.casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class), anyInt(), any())).thenReturn(false);
        assertThatThrownBy(() -> notify.onDeal(ctx("9.90", 100)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
    }

    @Test
    void closedOrderThrowsMatchConflict() {
        TradeOrder filled = buyOrder(100, 100, "0.00");
        filled.setStatus(OrderStateMachine.FILLED);
        orders(filled, sellOrder(100, 0, 100));
        assertThatThrownBy(() -> notify.onDeal(ctx("9.90", 100)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
    }

    @Test
    void insufficientRemainQtyThrowsMatchConflict() {
        orders(buyOrder(100, 0, "1015.00"), sellOrder(100, 0, 100));
        assertThatThrownBy(() -> notify.onDeal(ctx("9.90", 200)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_CONFLICT));
    }
}