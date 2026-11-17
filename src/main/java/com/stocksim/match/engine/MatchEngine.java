package com.stocksim.match.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.match.book.BookOrder;
import com.stocksim.match.book.InMemoryOrderBook;
import com.stocksim.match.book.OrderBook;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.match.notify.DealNotify;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.event.OrderCreatedEvent;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.service.StockService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 撮合引擎：每股分布式锁串行 + 内存盘口（R1规划§6.6，M2 接入 LockProvider）。
 * 下单 T1 提交后 AFTER_COMMIT 触发；每笔成交一个 T2，记账成功才扣减盘口、推进 deal_seq。
 */
@Component
public class MatchEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchEngine.class);
    private static final int MAX_CAS_RETRY = 3;

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();

    private final TradeOrderMapper tradeOrderMapper;
    private final DealRecordMapper dealRecordMapper;
    private final StockService stockService;
    private final DealNotify dealNotify;
    private final BizNoGenerator bizNoGenerator;
    private final DealSeq dealSeq;
    private final LockProvider lockProvider;

    public MatchEngine(TradeOrderMapper tradeOrderMapper, DealRecordMapper dealRecordMapper,
                       StockService stockService, DealNotify dealNotify,
                       BizNoGenerator bizNoGenerator, DealSeq dealSeq, LockProvider lockProvider) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.dealRecordMapper = dealRecordMapper;
        this.stockService = stockService;
        this.dealNotify = dealNotify;
        this.bizNoGenerator = bizNoGenerator;
        this.dealSeq = dealSeq;
        this.lockProvider = lockProvider;
    }

    /** 启动重建（R1规划§6.7）：deal_seq 取 DB MAX，未成交完的挂单按 create_time 入盘，不重放记账 */
    @PostConstruct
    public void rebuild() {
        Long max = dealRecordMapper.selectMaxDealSeq();
        dealSeq.init(max == null ? 0L : max);
        List<TradeOrder> openOrders = tradeOrderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .in(TradeOrder::getStatus, OrderStateMachine.PENDING, OrderStateMachine.PARTIAL_FILLED)
                        .apply("entrust_qty > matched_qty")
                        .orderByAsc(TradeOrder::getStockCode, TradeOrder::getCreateTime, TradeOrder::getId));
        for (TradeOrder order : openOrders) {
            bookOf(order.getStockCode()).add(toBookOrder(order));
        }
        log.info("盘口重建完成: 挂单 {} 笔, dealSeq={}", openOrders.size(), dealSeq.current());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            onNewOrder(event.stockCode(), event.orderNo());
        } catch (Exception e) {
            log.error("下单提交后撮合异常, orderNo={}", event.orderNo(), e);
        }
    }

    public void onNewOrder(String stockCode, String orderNo) {
        Lock lock = lockOf(stockCode);
        boolean acquired;
        try {
            acquired = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("撮合加锁被中断, 留单待下次触发, stockCode={}, orderNo={}", stockCode, orderNo);
            return;
        }
        if (!acquired) {
            log.warn("撮合加锁超时, 留单待下次触发, stockCode={}, orderNo={}", stockCode, orderNo);
            return;
        }
        try {
            OrderBook book = bookOf(stockCode);
            if (!book.contains(orderNo)) {
                TradeOrder order = tradeOrderMapper.selectOne(
                        new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo));
                if (order != null && isOpen(order) && order.getEntrustQty() > order.getMatchedQty()) {
                    book.add(toBookOrder(order));
                }
            }
            matchLoop(stockCode, book);
        } finally {
            lock.unlock();
        }
    }

    private void matchLoop(String stockCode, OrderBook book) {
        StockResponse stock = stockService.getStock(stockCode);
        while (true) {
            BookOrder bid = book.bestBid();
            BookOrder ask = book.bestAsk();
            if (bid == null || ask == null) {
                break;
            }
            if (bid.getPrice().compareTo(ask.getPrice()) < 0) {
                break;
            }
            // P3 自成交防护：同用户买卖不互撮
            if (bid.getUserId().equals(ask.getUserId())) {
                break;
            }
            // P9 停牌股不撮合，挂单保留
            if (stock.status() == null || stock.status() != 1) {
                break;
            }
            BookOrder resting = earlier(bid, ask);
            BigDecimal price = resting.getPrice();
            long qty = Math.min(bid.getRemainQty(), ask.getRemainQty());
            if (!executeWithRetry(stockCode, price, qty, bid, ask)) {
                break;
            }
            book.reduce(bid, qty);
            book.reduce(ask, qty);
        }
    }

    /** 每笔成交 ≤3 次重试，每次换新 dealNo/dealSeq；仅 CAS 类冲突可重试，其余异常留单待下次触发 */
    private boolean executeWithRetry(String stockCode, BigDecimal price, long qty,
                                     BookOrder bid, BookOrder ask) {
        for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
            String dealNo = bizNoGenerator.dealNo();
            long seq = dealSeq.next();
            DealContext ctx = new DealContext(dealNo, seq, stockCode, price, qty,
                    bid.getOrderNo(), ask.getOrderNo(), bid.getUserId(), ask.getUserId());
            try {
                dealNotify.onDeal(ctx);
                dealSeq.commit(seq);
                return true;
            } catch (BizException e) {
                if (e.getErrorCode() == ErrorCode.SYSTEM_BUSY || e.getErrorCode() == ErrorCode.MATCH_CONFLICT) {
                    log.warn("成交记账CAS冲突重试, dealNo={}, attempt={}, code={}", dealNo, attempt, e.getErrorCode());
                    continue;
                }
                log.error("成交记账失败(不可重试), dealNo={}", dealNo, e);
                return false;
            } catch (Exception e) {
                log.error("成交记账异常, dealNo={}", dealNo, e);
                return false;
            }
        }
        log.error("成交CAS重试耗尽, stockCode={}, qty={}", stockCode, qty);
        return false;
    }

    private static BookOrder earlier(BookOrder bid, BookOrder ask) {
        int cmp = bid.getCreateTime().compareTo(ask.getCreateTime());
        if (cmp != 0) {
            return cmp < 0 ? bid : ask;
        }
        return bid.getOrderId() <= ask.getOrderId() ? bid : ask;
    }

    private static boolean isOpen(TradeOrder order) {
        return order.getStatus() == OrderStateMachine.PENDING
                || order.getStatus() == OrderStateMachine.PARTIAL_FILLED;
    }

    private static BookOrder toBookOrder(TradeOrder order) {
        return new BookOrder(order.getId(), order.getOrderNo(), order.getUserId(), order.getStockCode(),
                order.getDirection(), order.getEntrustPrice(),
                order.getEntrustQty() - order.getMatchedQty(), order.getCreateTime());
    }

    public Lock lockOf(String stockCode) {
        return lockProvider.stockLock(stockCode);
    }

    public OrderBook bookOf(String stockCode) {
        return books.computeIfAbsent(stockCode, k -> new InMemoryOrderBook());
    }
}