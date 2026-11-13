package com.stocksim.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.Amounts;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.PageResult;
import com.stocksim.common.TradeCalendar;
import com.stocksim.common.TradingSession;
import com.stocksim.common.properties.TradeProperties;
import com.stocksim.order.dto.CreateOrderRequest;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.event.OrderCreatedEvent;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.FeeCalculator;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.dto.PositionResponse;
import com.stocksim.position.service.PositionService;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.service.QuoteService;
import com.stocksim.stock.service.StockService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int MAX_CAS_RETRY = 3;
    private static final int DIRECTION_BUY = 1;
    private static final int DIRECTION_SELL = 2;

    private final TradeOrderMapper tradeOrderMapper;
    private final StockService stockService;
    private final QuoteService quoteService;
    private final AccountService accountService;
    private final PositionService positionService;
    private final FeeCalculator feeCalculator;
    private final BizNoGenerator bizNoGenerator;
    private final TradeProperties tradeProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public OrderServiceImpl(TradeOrderMapper tradeOrderMapper, StockService stockService, QuoteService quoteService,
                            AccountService accountService, PositionService positionService,
                            FeeCalculator feeCalculator, BizNoGenerator bizNoGenerator,
                            TradeProperties tradeProperties, TransactionTemplate transactionTemplate,
                            ApplicationEventPublisher eventPublisher) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.stockService = stockService;
        this.quoteService = quoteService;
        this.accountService = accountService;
        this.positionService = positionService;
        this.feeCalculator = feeCalculator;
        this.bizNoGenerator = bizNoGenerator;
        this.tradeProperties = tradeProperties;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        TradeOrder existing = findByRequest(userId, request.requestId());
        if (existing != null) {
            return toResponse(existing);
        }
        if (tradeProperties.isSessionCheckEnabled()
                && !TradingSession.inSession(LocalDateTime.now(TradeCalendar.ZONE))) {
            throw new BizException(ErrorCode.NOT_TRADING_SESSION, "非交易时段");
        }
        StockResponse stock = stockService.getStock(request.stockCode());
        if (stock.status() == null || stock.status() != 1) {
            throw new BizException(ErrorCode.STOCK_SUSPENDED, "股票停牌或退市");
        }
        QuoteResponse quote = quoteService.getQuote(request.stockCode());
        if (request.price().compareTo(quote.limitUpPrice()) > 0
                || request.price().compareTo(quote.limitDownPrice()) < 0) {
            throw new BizException(ErrorCode.PRICE_LIMIT, "委托价超出涨跌停范围");
        }
        boolean buy = request.direction() == DIRECTION_BUY;
        PositionResponse position = buy ? null : positionService.getPosition(userId, request.stockCode());
        validateLot(request, stock, position);
        if (!buy) {
            validateSellPosition(request.quantity(), position);
        }
        BigDecimal amount = Amounts.round2(request.price().multiply(BigDecimal.valueOf(request.quantity())));
        BigDecimal freezeAmount = buy ? amount.add(feeCalculator.buyFeeEstimate(amount)) : BigDecimal.ZERO;
        AccountResponse account = accountService.getAccount(userId);
        if (buy && account.availableAmount().compareTo(freezeAmount) < 0) {
            throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH, "可用资金不足");
        }
        if (account.status() == null || account.status() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_FROZEN, "账户状态异常");
        }
        for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
            try {
                // RR 隔离级别下同事务重读仍是旧快照，CAS 重试必须每次开新事务
                return transactionTemplate.execute(status -> doCreateOrder(userId, request, quote, freezeAmount));
            } catch (DuplicateKeyException e) {
                return toResponse(requireByRequest(userId, request.requestId()));
            } catch (BizException e) {
                if (e.getErrorCode() != ErrorCode.SYSTEM_BUSY) {
                    throw e;
                }
                // 账户/持仓 CAS 冲突，换事务重试
            }
        }
        throw new BizException(ErrorCode.SYSTEM_BUSY, "下单并发冲突，请重试");
    }

    @Override
    public PageResult<OrderResponse> listOrders(Long userId, long page, long size, Integer status,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        Page<TradeOrder> result = tradeOrderMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getUserId, userId)
                        .eq(status != null, TradeOrder::getStatus, status)
                        .ge(startTime != null, TradeOrder::getCreateTime, startTime)
                        .le(endTime != null, TradeOrder::getCreateTime, endTime)
                        .orderByDesc(TradeOrder::getCreateTime)
                        .orderByDesc(TradeOrder::getId));
        List<OrderResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public OrderResponse getOrder(Long userId, String orderNo) {
        TradeOrder order = tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getUserId, userId)
                        .eq(TradeOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        return toResponse(order);
    }

    @Override
    public boolean casTransit(TradeOrder current, int toStatus, TradeOrder update) {
        OrderStateMachine.assertTransit(current.getStatus(), toStatus);
        update.setId(current.getId());
        update.setVersion(current.getVersion());
        update.setStatus(toStatus);
        return tradeOrderMapper.updateById(update) > 0;
    }

    private OrderResponse doCreateOrder(Long userId, CreateOrderRequest request, QuoteResponse quote,
                                        BigDecimal freezeAmount) {
        TradeOrder existing = findByRequest(userId, request.requestId());
        if (existing != null) {
            return toResponse(existing);
        }
        boolean buy = request.direction() == DIRECTION_BUY;
        String orderNo = bizNoGenerator.orderNo();
        TradeOrder order = new TradeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setRequestId(request.requestId());
        order.setTradeDate(quote.tradeDate());
        order.setStockCode(request.stockCode());
        order.setDirection(request.direction());
        order.setOrderType(1);
        order.setEntrustPrice(request.price());
        order.setEntrustQty(request.quantity());
        order.setMatchedQty(0L);
        order.setMatchedAmount(BigDecimal.ZERO);
        order.setFrozenAmount(buy ? freezeAmount : BigDecimal.ZERO);
        order.setFrozenQty(buy ? 0L : request.quantity());
        order.setFeeAmount(BigDecimal.ZERO);
        order.setStatus(OrderStateMachine.PENDING);
        order.setExpireTime(quote.tradeDate().atTime(15, 0));
        order.setSource(1);
        if (buy) {
            accountService.freeze(userId, freezeAmount, BizNoGenerator.orderFreezeFund(orderNo), orderNo);
        } else {
            positionService.freeze(userId, request.stockCode(), request.quantity(),
                    BizNoGenerator.orderFreezePosition(orderNo), orderNo);
        }
        tradeOrderMapper.insert(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getStockCode(), order.getOrderNo()));
        return toResponse(order);
    }

    private void validateLot(CreateOrderRequest request, StockResponse stock, PositionResponse position) {
        long qty = request.quantity();
        int lotSize = stock.lotSize();
        if (request.direction() == DIRECTION_BUY) {
            if (qty % lotSize != 0) {
                throw new BizException(ErrorCode.NOT_LOT_SIZE, "买入数量必须为整手");
            }
        } else if (qty % lotSize != 0 && (position == null || qty != position.availableQty())) {
            // 卖出零股须一次性委托全部可卖持仓
            throw new BizException(ErrorCode.NOT_LOT_SIZE, "零股卖出须一次性委托");
        }
    }

    private void validateSellPosition(long qty, PositionResponse position) {
        if (position == null) {
            throw new BizException(ErrorCode.POSITION_NOT_ENOUGH, "持仓不存在");
        }
        if (position.availableQty() < qty) {
            if (position.totalQty() - position.frozenQty() >= qty) {
                throw new BizException(ErrorCode.T_PLUS_ONE, "当日买入股份当日不可卖");
            }
            throw new BizException(ErrorCode.POSITION_NOT_ENOUGH, "可卖持仓不足");
        }
    }

    private TradeOrder findByRequest(Long userId, String requestId) {
        return tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getUserId, userId)
                        .eq(TradeOrder::getRequestId, requestId));
    }

    private TradeOrder requireByRequest(Long userId, String requestId) {
        TradeOrder order = findByRequest(userId, requestId);
        if (order == null) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "幂等订单丢失");
        }
        return order;
    }

    private OrderResponse toResponse(TradeOrder order) {
        return new OrderResponse(order.getOrderNo(), order.getStockCode(), order.getDirection(),
                order.getOrderType(), order.getEntrustPrice(), order.getEntrustQty(), order.getMatchedQty(),
                order.getMatchedAmount(), order.getFrozenAmount(), order.getFrozenQty(), order.getFeeAmount(),
                order.getStatus(), order.getTradeDate(), order.getExpireTime(), order.getRejectReason(),
                order.getCreateTime());
    }
}