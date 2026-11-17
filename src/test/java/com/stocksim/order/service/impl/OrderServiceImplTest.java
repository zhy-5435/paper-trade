package com.stocksim.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.PageResult;
import com.stocksim.common.TradingSession;
import com.stocksim.common.lock.LocalLockProvider;
import com.stocksim.common.properties.TradeProperties;
import com.stocksim.order.dto.CreateOrderRequest;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.util.FeeCalculator;
import com.stocksim.position.dto.PositionResponse;
import com.stocksim.position.service.PositionService;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.service.QuoteService;
import com.stocksim.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 13);

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
    private com.stocksim.common.BizNoGenerator bizNoGenerator;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TradeProperties tradeProperties;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        tradeProperties = new TradeProperties();
        FeeCalculator feeCalculator = new FeeCalculator(tradeProperties);
        orderService = new OrderServiceImpl(tradeOrderMapper, stockService, quoteService, accountService,
                positionService, feeCalculator, bizNoGenerator, tradeProperties, transactionTemplate, eventPublisher,
                new LocalLockProvider());
    }

    private void txRunsCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    private StockResponse stock(int status, int lotSize) {
        return new StockResponse("600000", "浦发银行", 1, 1, new BigDecimal("10.00"),
                new BigDecimal("0.10"), lotSize, status, LocalDate.of(2020, 1, 1));
    }

    private QuoteResponse quote(String limitUp, String limitDown) {
        return new QuoteResponse("600000", TRADE_DATE, new BigDecimal("10.00"), new BigDecimal("10.00"),
                new BigDecimal("10.00"), new BigDecimal("10.50"), new BigDecimal("9.50"),
                new BigDecimal(limitUp), new BigDecimal(limitDown), 1000L, new BigDecimal("10000.00"),
                BigDecimal.ZERO, LocalDateTime.now());
    }

    private AccountResponse account(String available, int status) {
        return new AccountResponse("A1", "CNY", new BigDecimal(available), BigDecimal.ZERO,
                new BigDecimal(available), new BigDecimal(available), status);
    }

    private PositionResponse position(long total, long frozen, long todayBuy) {
        return new PositionResponse("600000", total, frozen, todayBuy, total - frozen - todayBuy,
                new BigDecimal("10000.00"), new BigDecimal("10.00"), new BigDecimal("10.00"),
                new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private CreateOrderRequest buy(String requestId, String price, long qty) {
        return new CreateOrderRequest(requestId, "600000", 1, new BigDecimal(price), qty);
    }

    private CreateOrderRequest sell(String requestId, String price, long qty) {
        return new CreateOrderRequest(requestId, "600000", 2, new BigDecimal(price), qty);
    }

    private TradeOrder existingOrder() {
        TradeOrder o = new TradeOrder();
        o.setId(10L);
        o.setOrderNo("O999");
        o.setUserId(2L);
        o.setRequestId("r1");
        o.setStockCode("600000");
        o.setDirection(1);
        o.setOrderType(1);
        o.setEntrustPrice(new BigDecimal("10.00"));
        o.setEntrustQty(100L);
        o.setMatchedQty(0L);
        o.setMatchedAmount(BigDecimal.ZERO);
        o.setFrozenAmount(new BigDecimal("1005.00"));
        o.setFrozenQty(0L);
        o.setFeeAmount(BigDecimal.ZERO);
        o.setStatus(1);
        o.setTradeDate(TRADE_DATE);
        o.setExpireTime(TRADE_DATE.atTime(15, 0));
        o.setVersion(0);
        o.setCreateTime(LocalDateTime.now());
        return o;
    }

    private void stubListedStock() {
        when(stockService.getStock("600000")).thenReturn(stock(1, 100));
        when(quoteService.getQuote("600000")).thenReturn(quote("11.00", "9.00"));
    }

    @Test
    void 买单成功_冻结1005_状态1() {
        txRunsCallback();
        stubListedStock();
        when(accountService.getAccount(2L)).thenReturn(account("100000.00", 1));
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);
        when(bizNoGenerator.orderNo()).thenReturn("O1");

        OrderResponse resp = orderService.createOrder(2L, buy("r1", "10.00", 100));

        assertThat(resp.orderNo()).isEqualTo("O1");
        assertThat(resp.status()).isEqualTo(1);
        assertThat(resp.direction()).isEqualTo(1);
        assertThat(resp.frozenAmount()).isEqualByComparingTo("1005.00");
        assertThat(resp.frozenQty()).isEqualTo(0);
        assertThat(resp.tradeDate()).isEqualTo(TRADE_DATE);
        assertThat(resp.expireTime()).isEqualTo(TRADE_DATE.atTime(15, 0));

        verify(accountService).freeze(eq(2L), eq(new BigDecimal("1005.00")), eq("O1F3"), eq("O1"));
        ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(tradeOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getFrozenAmount()).isEqualByComparingTo("1005.00");
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getRequestId()).isEqualTo("r1");
        verify(positionService, never()).freeze(any(), any(), anyLong(), any(), any());
    }

    @Test
    void 卖单成功_冻结持仓100() {
        txRunsCallback();
        stubListedStock();
        when(positionService.getPosition(2L, "600000")).thenReturn(position(1000, 0, 0));
        when(accountService.getAccount(2L)).thenReturn(account("0.00", 1));
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);
        when(bizNoGenerator.orderNo()).thenReturn("O2");

        OrderResponse resp = orderService.createOrder(2L, sell("s1", "10.00", 100));

        assertThat(resp.direction()).isEqualTo(2);
        assertThat(resp.frozenQty()).isEqualTo(100);
        assertThat(resp.frozenAmount()).isEqualByComparingTo("0.00");
        verify(positionService).freeze(2L, "600000", 100L, "O2P4", "O2");
        verify(accountService, never()).freeze(any(), any(), any(), any());
    }

    @Test
    void 重复requestId_返回原单不冻结() {
        when(tradeOrderMapper.selectOne(any())).thenReturn(existingOrder());

        OrderResponse resp = orderService.createOrder(2L, buy("r1", "10.00", 100));

        assertThat(resp.orderNo()).isEqualTo("O999");
        verify(accountService, never()).freeze(any(), any(), any(), any());
        verify(tradeOrderMapper, never()).insert(any(TradeOrder.class));
        verifyNoInteractions(stockService, quoteService, transactionTemplate);
    }

    @Test
    void 非交易时段_3005() {
        tradeProperties.setSessionCheckEnabled(true);
        try (MockedStatic<TradingSession> session = mockStatic(TradingSession.class)) {
            session.when(() -> TradingSession.inSession(any(LocalDateTime.class))).thenReturn(false);

            assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "10.00", 100)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode().getCode())
                    .isEqualTo(3005);
        }
        verifyNoInteractions(stockService);
    }

    @Test
    void 停牌_3004() {
        when(stockService.getStock("600000")).thenReturn(stock(2, 100));

        assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "10.00", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3004);
        verifyNoInteractions(quoteService);
    }

    @Test
    void 超涨停_3001() {
        stubListedStock();

        assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "11.01", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3001);
    }

    @Test
    void 低于跌停_3001() {
        stubListedStock();

        assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "8.99", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3001);
    }

    @Test
    void 买入非整手_3002() {
        stubListedStock();

        assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "10.00", 150)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3002);
        verifyNoInteractions(accountService);
    }

    @Test
    void 卖出零股非一次性_3002() {
        stubListedStock();
        when(positionService.getPosition(2L, "600000")).thenReturn(position(1000, 0, 0));

        assertThatThrownBy(() -> orderService.createOrder(2L, sell("s1", "10.00", 50)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3002);
    }

    @Test
    void 卖出零股一次性清仓_成功() {
        txRunsCallback();
        stubListedStock();
        when(positionService.getPosition(2L, "600000")).thenReturn(position(50, 0, 0));
        when(accountService.getAccount(2L)).thenReturn(account("0.00", 1));
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);
        when(bizNoGenerator.orderNo()).thenReturn("O3");

        OrderResponse resp = orderService.createOrder(2L, sell("s1", "10.00", 50));

        assertThat(resp.frozenQty()).isEqualTo(50);
        verify(positionService).freeze(2L, "600000", 50L, "O3P4", "O3");
    }

    @Test
    void 当日买入不可卖_3003() {
        stubListedStock();
        when(positionService.getPosition(2L, "600000")).thenReturn(position(100, 0, 100));

        assertThatThrownBy(() -> orderService.createOrder(2L, sell("s1", "10.00", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(3003);
    }

    @Test
    void 可卖持仓不足_2003() {
        stubListedStock();
        when(positionService.getPosition(2L, "600000")).thenReturn(position(100, 0, 0));

        assertThatThrownBy(() -> orderService.createOrder(2L, sell("s1", "10.00", 200)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2003);
    }

    @Test
    void 无持仓卖出_2003() {
        stubListedStock();
        when(positionService.getPosition(2L, "600000")).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(2L, sell("s1", "10.00", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2003);
    }

    @Test
    void 可用资金不足_2001() {
        stubListedStock();
        when(accountService.getAccount(2L)).thenReturn(account("1000.00", 1));

        assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "10.00", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2001);
        verify(tradeOrderMapper, never()).insert(any(TradeOrder.class));
    }

    @Test
    void 账户冻结_2002() {
        stubListedStock();
        when(accountService.getAccount(2L)).thenReturn(account("100000.00", 0));

        assertThatThrownBy(() -> orderService.createOrder(2L, buy("r1", "10.00", 100)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2002);
    }

    @Test
    void 并发重复提交_唯一键冲突返回原单() {
        txRunsCallback();
        stubListedStock();
        when(accountService.getAccount(2L)).thenReturn(account("100000.00", 1));
        when(tradeOrderMapper.selectOne(any())).thenReturn(null, null, existingOrder());
        when(bizNoGenerator.orderNo()).thenReturn("O1");
        when(tradeOrderMapper.insert(any(TradeOrder.class))).thenThrow(new DuplicateKeyException("uk_user_request"));

        OrderResponse resp = orderService.createOrder(2L, buy("r1", "10.00", 100));

        assertThat(resp.orderNo()).isEqualTo("O999");
    }

    @Test
    void CAS冲突_换事务重试后成功() {
        txRunsCallback();
        stubListedStock();
        when(accountService.getAccount(2L)).thenReturn(account("100000.00", 1));
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);
        when(bizNoGenerator.orderNo()).thenReturn("O1");
        doThrow(new BizException(ErrorCode.SYSTEM_BUSY, "账户并发冲突"))
                .doNothing()
                .when(accountService).freeze(eq(2L), any(), eq("O1F3"), eq("O1"));

        OrderResponse resp = orderService.createOrder(2L, buy("r1", "10.00", 100));

        assertThat(resp.orderNo()).isEqualTo("O1");
        verify(accountService, times(2)).freeze(eq(2L), any(), eq("O1F3"), eq("O1"));
        verify(tradeOrderMapper, times(1)).insert(any(TradeOrder.class));
    }

    @Test
    void casTransit合法迁移_设置id版本状态() {
        TradeOrder current = existingOrder();
        TradeOrder update = new TradeOrder();
        update.setMatchedQty(50L);
        when(tradeOrderMapper.updateById(any(TradeOrder.class))).thenReturn(1);

        boolean ok = orderService.casTransit(current, 2, update);

        assertThat(ok).isTrue();
        ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(tradeOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
        assertThat(captor.getValue().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getVersion()).isEqualTo(0);
        assertThat(captor.getValue().getMatchedQty()).isEqualTo(50L);
    }

    @Test
    void casTransit非法迁移_抛5001() {
        TradeOrder current = existingOrder();
        current.setStatus(3);

        assertThatThrownBy(() -> orderService.casTransit(current, 2, new TradeOrder()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(tradeOrderMapper, never()).updateById(any(TradeOrder.class));
    }

    @Test
    void casTransit版本冲突_返回false() {
        TradeOrder current = existingOrder();
        when(tradeOrderMapper.updateById(any(TradeOrder.class))).thenReturn(0);

        assertThat(orderService.casTransit(current, 3, new TradeOrder())).isFalse();
    }

    @Test
    void 订单详情_不存在抛1001() {
        when(tradeOrderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> orderService.getOrder(2L, "O404"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(1001);
    }

    @Test
    void 订单详情_携带userId返回() {
        when(tradeOrderMapper.selectOne(any())).thenReturn(existingOrder());

        assertThat(orderService.getOrder(2L, "O999").orderNo()).isEqualTo("O999");
    }

    @Test
    void 订单分页() {
        Page<TradeOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(existingOrder()));
        page.setTotal(1);
        when(tradeOrderMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OrderResponse> result = orderService.listOrders(2L, 1, 10, null, null, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).orderNo()).isEqualTo("O999");
    }
}