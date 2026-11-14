package com.stocksim.match.engine;

import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.match.book.BookOrder;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.match.notify.DealNotify;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchEngineTest {

    private static final String STOCK = "600000";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 13, 9, 30, 0);

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private DealRecordMapper dealRecordMapper;
    @Mock
    private StockService stockService;
    @Mock
    private DealNotify dealNotify;
    @Mock
    private BizNoGenerator bizNoGenerator;

    private DealSeq dealSeq;
    private MatchEngine engine;
    private final AtomicInteger dealNoCounter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        dealSeq = new DealSeq();
        engine = new MatchEngine(tradeOrderMapper, dealRecordMapper, stockService, dealNotify, bizNoGenerator, dealSeq);
        lenient().when(stockService.getStock(STOCK)).thenReturn(stock(1));
        lenient().when(bizNoGenerator.dealNo()).thenAnswer(inv -> "D" + dealNoCounter.incrementAndGet());
    }

    private StockResponse stock(int status) {
        return new StockResponse(STOCK, "浦发银行", 1, 1, new BigDecimal("10.00"),
                new BigDecimal("0.10"), 100, status, LocalDate.of(2020, 1, 1));
    }

    private BookOrder bookOrder(long id, String orderNo, long userId, int direction, String price,
                                long qty, LocalDateTime createTime) {
        return new BookOrder(id, orderNo, userId, STOCK, direction, new BigDecimal(price), qty, createTime);
    }

    private TradeOrder tradeOrder(long id, String orderNo, long userId, int direction, String price,
                                  long entrust, long matched) {
        TradeOrder order = new TradeOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStockCode(STOCK);
        order.setDirection(direction);
        order.setEntrustPrice(new BigDecimal(price));
        order.setEntrustQty(entrust);
        order.setMatchedQty(matched);
        order.setStatus(OrderStateMachine.PENDING);
        order.setCreateTime(T0);
        return order;
    }

    private DealContext captureSingleDeal() {
        ArgumentCaptor<DealContext> captor = ArgumentCaptor.forClass(DealContext.class);
        verify(dealNotify).onDeal(captor.capture());
        return captor.getValue();
    }

    @Test
    void dealPriceIsRestingOrderPrice() {
        // user02 先挂卖 @9.90，user01 后挂买 @10.10 → 成交 @9.90（R1规划§6.4）
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.10", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        DealContext ctx = captureSingleDeal();
        assertThat(ctx.dealPrice()).isEqualByComparingTo("9.90");
        assertThat(ctx.dealQty()).isEqualTo(100);
        assertThat(ctx.buyerOrderNo()).isEqualTo("B1");
        assertThat(ctx.sellerOrderNo()).isEqualTo("S1");
        assertThat(engine.bookOf(STOCK).size()).isZero();
        assertThat(dealSeq.committed()).isEqualTo(1);
    }

    @Test
    void partialFillLeavesRemainderOnBook() {
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 200, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        DealContext ctx = captureSingleDeal();
        assertThat(ctx.dealQty()).isEqualTo(100);
        assertThat(engine.bookOf(STOCK).bestAsk()).isNull();
        assertThat(engine.bookOf(STOCK).bestBid().getRemainQty()).isEqualTo(100);
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(1);
    }

    @Test
    void selfTradeIsSkipped() {
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 1L, 2, "9.90", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        verify(dealNotify, never()).onDeal(any());
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(2);
        assertThat(dealSeq.committed()).isZero();
    }

    @Test
    void suspendedStockIsNotMatched() {
        when(stockService.getStock(STOCK)).thenReturn(stock(2));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        verify(dealNotify, never()).onDeal(any());
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(2);
    }

    @Test
    void noCrossPriceNoDeal() {
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "9.80", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        verify(dealNotify, never()).onDeal(any());
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(2);
    }

    @Test
    void casConflictRetriesThenSucceeds() {
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "冲突"))
                .doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "冲突"))
                .doNothing().when(dealNotify).onDeal(any());
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        verify(dealNotify, times(3)).onDeal(any());
        assertThat(engine.bookOf(STOCK).size()).isZero();
        assertThat(dealSeq.current()).isEqualTo(3);
        assertThat(dealSeq.committed()).isEqualTo(3);
    }

    @Test
    void casConflictExhaustedLeavesOrdersOnBook() {
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "冲突")).when(dealNotify).onDeal(any());
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        verify(dealNotify, times(3)).onDeal(any());
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(2);
        assertThat(dealSeq.current()).isEqualTo(3);
        assertThat(dealSeq.committed()).isZero();
    }

    @Test
    void nonRetryableErrorBreaksImmediately() {
        doThrow(new BizException(ErrorCode.PARAM_ERROR, "非法")).when(dealNotify).onDeal(any());
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 100, T0.plusSeconds(1)));
        engine.onNewOrder(STOCK, "B1");
        verify(dealNotify, times(1)).onDeal(any());
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(2);
        assertThat(dealSeq.committed()).isZero();
    }

    @Test
    void multipleDealsAdvanceSeqMonotonically() {
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        engine.bookOf(STOCK).add(bookOrder(3, "S2", 3L, 2, "9.90", 100, T0.plusSeconds(1)));
        engine.bookOf(STOCK).add(bookOrder(1, "B1", 1L, 1, "10.00", 200, T0.plusSeconds(2)));
        engine.onNewOrder(STOCK, "B1");
        ArgumentCaptor<DealContext> captor = ArgumentCaptor.forClass(DealContext.class);
        verify(dealNotify, times(2)).onDeal(captor.capture());
        List<DealContext> deals = captor.getAllValues();
        assertThat(deals.get(0).dealSeq()).isEqualTo(1);
        assertThat(deals.get(1).dealSeq()).isEqualTo(2);
        assertThat(deals.get(0).sellerOrderNo()).isEqualTo("S1");
        assertThat(deals.get(1).sellerOrderNo()).isEqualTo("S2");
        assertThat(engine.bookOf(STOCK).size()).isZero();
        assertThat(dealSeq.committed()).isEqualTo(2);
    }

    @Test
    void newOrderLoadedFromDbTriggersMatch() {
        engine.bookOf(STOCK).add(bookOrder(2, "S1", 2L, 2, "9.90", 100, T0));
        TradeOrder bid = tradeOrder(1, "B1", 1L, 1, "10.10", 100, 0);
        bid.setCreateTime(T0.plusSeconds(1));
        when(tradeOrderMapper.selectOne(any())).thenReturn(bid);
        engine.onNewOrder(STOCK, "B1");
        DealContext ctx = captureSingleDeal();
        assertThat(ctx.dealPrice()).isEqualByComparingTo("9.90");
        assertThat(engine.bookOf(STOCK).size()).isZero();
    }

    @Test
    void closedOrderIsNotAddedToBook() {
        TradeOrder filled = tradeOrder(1, "B1", 1L, 1, "10.00", 100, 100);
        filled.setStatus(OrderStateMachine.FILLED);
        when(tradeOrderMapper.selectOne(any())).thenReturn(filled);
        engine.onNewOrder(STOCK, "B1");
        assertThat(engine.bookOf(STOCK).size()).isZero();
        verify(dealNotify, never()).onDeal(any());
    }

    @Test
    void rebuildInitsSeqAndRestoresOpenOrders() {
        when(dealRecordMapper.selectMaxDealSeq()).thenReturn(50L);
        TradeOrder buy = tradeOrder(1, "B1", 1L, 1, "10.00", 200, 100);
        TradeOrder sell = tradeOrder(2, "S1", 2L, 2, "9.90", 100, 0);
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(buy, sell));
        engine.rebuild();
        assertThat(dealSeq.current()).isEqualTo(50);
        assertThat(dealSeq.committed()).isEqualTo(50);
        assertThat(engine.bookOf(STOCK).size()).isEqualTo(2);
        assertThat(engine.bookOf(STOCK).bestBid().getRemainQty()).isEqualTo(100);
        verify(dealNotify, never()).onDeal(any());
    }

    @Test
    void rebuildWithNoDealsInitsSeqToZero() {
        when(dealRecordMapper.selectMaxDealSeq()).thenReturn(null);
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of());
        engine.rebuild();
        assertThat(dealSeq.current()).isZero();
        assertThat(engine.bookOf(STOCK).size()).isZero();
    }
}