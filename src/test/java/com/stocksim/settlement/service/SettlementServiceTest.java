package com.stocksim.settlement.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.TradeCalendar;
import com.stocksim.match.book.BookOrder;
import com.stocksim.match.book.InMemoryOrderBook;
import com.stocksim.match.engine.MatchEngine;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.entity.UserPosition;
import com.stocksim.position.mapper.UserPositionMapper;
import com.stocksim.position.service.PositionService;
import com.stocksim.settlement.dto.SettlementResult;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.entity.StockQuote;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final String STOCK = "600000";
    private static final LocalDate D = LocalDate.of(2026, 11, 13);
    private static final LocalDate NEXT_D = TradeCalendar.nextTradingDay(D);

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

    private InMemoryOrderBook book;
    private SettlementService service;

    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StockQuote.class);
        TableInfoHelper.initTableInfo(assistant, UserPosition.class);
    }

    @BeforeEach
    void setUp() {
        book = new InMemoryOrderBook();
        service = new SettlementService(tradeOrderMapper, stockQuoteMapper, stockInfoMapper,
                userPositionMapper, accountService, positionService, orderService, matchEngine,
                transactionTemplate);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        lenient().when(matchEngine.lockOf(any())).thenReturn(new ReentrantLock());
        lenient().when(matchEngine.bookOf(any())).thenReturn(book);
        lenient().when(orderService.casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class)))
                .thenReturn(true);
    }

    private StockQuote quote(LocalDate tradeDate) {
        StockQuote q = new StockQuote();
        q.setId(1L);
        q.setStockCode(STOCK);
        q.setTradeDate(tradeDate);
        q.setPreClosePrice(new BigDecimal("10.00"));
        q.setLatestPrice(new BigDecimal("10.00"));
        q.setOpenPrice(new BigDecimal("10.00"));
        q.setHighPrice(new BigDecimal("10.50"));
        q.setLowPrice(new BigDecimal("9.50"));
        q.setLimitUpPrice(new BigDecimal("11.00"));
        q.setLimitDownPrice(new BigDecimal("9.00"));
        q.setTotalVolume(100L);
        q.setTotalAmount(new BigDecimal("1000.00"));
        q.setChangeRatio(BigDecimal.ZERO);
        q.setVersion(0);
        return q;
    }

    private TradeOrder buyOrder(int status, String frozenAmount) {
        TradeOrder o = new TradeOrder();
        o.setId(1L);
        o.setOrderNo("B1");
        o.setUserId(2L);
        o.setStockCode(STOCK);
        o.setDirection(1);
        o.setEntrustPrice(new BigDecimal("10.00"));
        o.setEntrustQty(100L);
        o.setMatchedQty(0L);
        o.setFrozenAmount(new BigDecimal(frozenAmount));
        o.setFrozenQty(0L);
        o.setStatus(status);
        o.setTradeDate(D);
        o.setVersion(0);
        return o;
    }

    private TradeOrder sellOrder(int status, long frozenQty) {
        TradeOrder o = new TradeOrder();
        o.setId(2L);
        o.setOrderNo("S1");
        o.setUserId(3L);
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

    private StockInfo stockInfo() {
        StockInfo s = new StockInfo();
        s.setStockCode(STOCK);
        s.setPriceLimitRatio(new BigDecimal("0.10"));
        return s;
    }

    private void addBuyToBook() {
        book.add(new BookOrder(1L, "B1", 2L, STOCK, 1, new BigDecimal("10.00"), 100L, LocalDateTime.now()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullRunExpiresBuyCutQuoteClearsTodayBuy() {
        StockQuote q = quote(D);
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, "1000.00");
        addBuyToBook();
        when(stockQuoteMapper.selectOne(any())).thenReturn(q, q);
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(stockInfoMapper.selectList(null)).thenReturn(List.of(stockInfo()));
        when(stockQuoteMapper.selectList(any())).thenReturn(List.of(q));
        when(stockQuoteMapper.update(any(), any())).thenReturn(1);
        when(userPositionMapper.update(any(), any())).thenReturn(2);

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isEqualTo(1);
        assertThat(result.cutQuotes()).isEqualTo(1);
        assertThat(result.clearedPositions()).isEqualTo(2);
        assertThat(result.closeDate()).isEqualTo(D);
        assertThat(result.nextDate()).isEqualTo(NEXT_D);
        assertThat(NEXT_D).isEqualTo(LocalDate.of(2026, 11, 16));

        verify(accountService).unfreeze(2L, new BigDecimal("1000.00"), "B1C4", "B1");
        verifyNoInteractions(positionService);
        assertThat(book.contains("B1")).isFalse();

        ArgumentCaptor<TradeOrder> update = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderService).casTransit(any(TradeOrder.class), eq(OrderStateMachine.EXPIRED), update.capture());
        assertThat(update.getValue().getRejectReason()).isEqualTo("日切过期撤单");

        ArgumentCaptor<LambdaUpdateWrapper<StockQuote>> quoteUpdate =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(stockQuoteMapper).update(any(), quoteUpdate.capture());
        Map<String, Object> params = quoteUpdate.getValue().getParamNameValuePairs();
        assertThat(params.values()).contains(NEXT_D, new BigDecimal("11.00"),
                new BigDecimal("9.00"), new BigDecimal("10.00"), 0L);
    }

    @Test
    void expireSellUnfreezesPosition() {
        TradeOrder order = sellOrder(OrderStateMachine.PENDING, 100L);
        book.add(new BookOrder(2L, "S1", 3L, STOCK, 2, new BigDecimal("10.00"), 100L, LocalDateTime.now()));
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isEqualTo(1);
        verify(positionService).unfreeze(3L, STOCK, 100L, "S1C5", "S1");
        verifyNoInteractions(accountService);
        assertThat(book.contains("S1")).isFalse();
    }

    @Test
    void expirePartialBuyUnfreezesExactRemainder() {
        TradeOrder order = buyOrder(OrderStateMachine.PARTIAL_FILLED, "1002.50");
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isEqualTo(1);
        verify(accountService).unfreeze(2L, new BigDecimal("1002.50"), "B1C4", "B1");
        verify(orderService).casTransit(any(TradeOrder.class), eq(OrderStateMachine.EXPIRED), any(TradeOrder.class));
    }

    @Test
    void skipOrderFilledOnReread() {
        TradeOrder scanned = buyOrder(OrderStateMachine.PENDING, "1000.00");
        TradeOrder reread = buyOrder(OrderStateMachine.FILLED, "0.00");
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(scanned));
        when(tradeOrderMapper.selectOne(any())).thenReturn(reread);

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isZero();
        verifyNoInteractions(accountService, positionService);
        verify(orderService, never()).casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class));
    }

    @Test
    void quoteIdempotentWhenAlreadyCut() {
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D), quote(NEXT_D));
        when(stockInfoMapper.selectList(null)).thenReturn(List.of(stockInfo()));
        when(stockQuoteMapper.selectList(any())).thenReturn(List.of(quote(D)));

        SettlementResult result = service.run();

        assertThat(result.cutQuotes()).isZero();
        verify(stockQuoteMapper, never()).update(any(), any());
    }

    @Test
    void quoteVersionConflictRetries() {
        StockQuote q = quote(D);
        when(stockQuoteMapper.selectOne(any())).thenReturn(q, q, q);
        when(stockInfoMapper.selectList(null)).thenReturn(List.of(stockInfo()));
        when(stockQuoteMapper.selectList(any())).thenReturn(List.of(q));
        when(stockQuoteMapper.update(any(), any())).thenReturn(0, 1);

        SettlementResult result = service.run();

        assertThat(result.cutQuotes()).isEqualTo(1);
        verify(stockQuoteMapper, times(2)).update(any(), any());
    }

    @Test
    void clearTodayBuyReturnsAffectedRows() {
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(userPositionMapper.update(any(), any())).thenReturn(3);

        SettlementResult result = service.run();

        assertThat(result.clearedPositions()).isEqualTo(3);
        assertThat(result.expiredOrders()).isZero();
        assertThat(result.cutQuotes()).isZero();
        verify(userPositionMapper).update(any(), any());
    }

    @Test
    void noQuotesEarlyReturn() {
        when(stockQuoteMapper.selectOne(any())).thenReturn(null);

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isZero();
        assertThat(result.cutQuotes()).isZero();
        assertThat(result.clearedPositions()).isZero();
        assertThat(result.closeDate()).isNull();
        assertThat(result.nextDate()).isNull();
        verifyNoInteractions(tradeOrderMapper, accountService, positionService, stockInfoMapper, userPositionMapper);
        verify(stockQuoteMapper, never()).selectList(any());
        verify(stockQuoteMapper, never()).update(any(), any());
    }

    @Test
    void expireUnfreezeConflictRetries() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, "1000.00");
        addBuyToBook();
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "busy")).doNothing()
                .when(accountService).unfreeze(any(), any(), any(), any());

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isEqualTo(1);
        verify(accountService, times(2)).unfreeze(2L, new BigDecimal("1000.00"), "B1C4", "B1");
        verify(orderService, times(1)).casTransit(any(TradeOrder.class), eq(OrderStateMachine.EXPIRED), any(TradeOrder.class));
        assertThat(book.contains("B1")).isFalse();
    }

    @Test
    void expireCasTransitConflictRetries() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, "1000.00");
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        when(orderService.casTransit(any(TradeOrder.class), anyInt(), any(TradeOrder.class)))
                .thenReturn(false, true);

        SettlementResult result = service.run();

        assertThat(result.expiredOrders()).isEqualTo(1);
        verify(orderService, times(2)).casTransit(any(TradeOrder.class), eq(OrderStateMachine.EXPIRED), any(TradeOrder.class));
        verify(accountService, times(2)).unfreeze(2L, new BigDecimal("1000.00"), "B1C4", "B1");
    }

    @Test
    void expireExhaustsRetriesThrows() {
        TradeOrder order = buyOrder(OrderStateMachine.PENDING, "1000.00");
        addBuyToBook();
        when(stockQuoteMapper.selectOne(any())).thenReturn(quote(D));
        when(tradeOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(tradeOrderMapper.selectOne(any())).thenReturn(order);
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "busy"))
                .when(accountService).unfreeze(any(), any(), any(), any());

        Throwable thrown = catchThrowable(() -> service.run());

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.SYSTEM_BUSY);
        verify(accountService, times(3)).unfreeze(2L, new BigDecimal("1000.00"), "B1C4", "B1");
        assertThat(book.contains("B1")).isTrue();
    }
}