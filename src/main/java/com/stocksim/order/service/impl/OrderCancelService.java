package com.stocksim.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.match.engine.MatchEngine;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 撤单核心（R2规划 Q3）：用户撤单与日切过期撤单共用。
 * 股票锁 → 在途记账检查（Σdeal_record.deal_qty == matched_qty，否则 4002/跳过）→ 状态 CAS →
 * 解冻剩余精确值（买 C4 / 卖 C5）→ 盘口移除 → 状态日志（casTransit 同事务，type 2/3）。
 * 锁在外层事务入口获取（Q2），先于事务提交释放无效。
 */
@Service
public class OrderCancelService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelService.class);
    private static final int MAX_CAS_RETRY = 3;
    private static final int DIRECTION_BUY = 1;
    private static final int CHANGE_USER_CANCEL = 2;
    private static final int CHANGE_EXPIRE = 3;

    private final TradeOrderMapper tradeOrderMapper;
    private final DealRecordMapper dealRecordMapper;
    private final AccountService accountService;
    private final PositionService positionService;
    private final OrderService orderService;
    private final MatchEngine matchEngine;
    private final TransactionTemplate transactionTemplate;
    private final LockProvider lockProvider;

    public OrderCancelService(TradeOrderMapper tradeOrderMapper, DealRecordMapper dealRecordMapper,
                              AccountService accountService, PositionService positionService,
                              OrderService orderService, MatchEngine matchEngine,
                              TransactionTemplate transactionTemplate, LockProvider lockProvider) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.dealRecordMapper = dealRecordMapper;
        this.accountService = accountService;
        this.positionService = positionService;
        this.orderService = orderService;
        this.matchEngine = matchEngine;
        this.transactionTemplate = transactionTemplate;
        this.lockProvider = lockProvider;
    }

    /** 用户主动撤单：属主校验 1003、状态预检 3007、在途/锁超时 4002、Redis 故障 5001。 */
    public OrderResponse cancelByUser(Long userId, String orderNo) {
        TradeOrder order = tradeOrderMapper.selectOne(byOrderNo(orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作他人订单");
        }
        if (!isOpen(order)) {
            throw new BizException(ErrorCode.CANCEL_NOT_ALLOWED, "当前状态不允许撤单");
        }
        String stockCode = order.getStockCode();
        Lock lock = lockProvider.stockLock(stockCode);
        boolean acquired;
        try {
            acquired = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.MATCH_CONFLICT, "撤单繁忙，请重试");
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "系统繁忙，请重试");
        }
        if (!acquired) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "撤单繁忙，请重试");
        }
        try {
            for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
                try {
                    Boolean done = transactionTemplate.execute(status -> {
                        TradeOrder cur = tradeOrderMapper.selectOne(byOrderNo(orderNo));
                        if (cur == null || !isOpen(cur)) {
                            throw new BizException(ErrorCode.CANCEL_NOT_ALLOWED, "当前状态不允许撤单");
                        }
                        assertNoInFlight(cur);
                        int toStatus = cur.getStatus() == OrderStateMachine.PENDING
                                ? OrderStateMachine.CANCELED : OrderStateMachine.PARTIAL_CANCELED;
                        TradeOrder update = unfreezeAndBuildUpdate(cur, "用户撤单");
                        if (!orderService.casTransit(cur, toStatus, update, CHANGE_USER_CANCEL, userId)) {
                            throw new BizException(ErrorCode.SYSTEM_BUSY, "订单CAS冲突，待重试");
                        }
                        return true;
                    });
                    if (Boolean.TRUE.equals(done)) {
                        matchEngine.bookOf(stockCode).remove(orderNo);
                        return orderService.getOrder(userId, orderNo);
                    }
                } catch (BizException e) {
                    if (e.getErrorCode() == ErrorCode.SYSTEM_BUSY) {
                        continue;
                    }
                    throw e;
                }
            }
            throw new BizException(ErrorCode.SYSTEM_BUSY, "撤单并发冲突: " + orderNo);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 日切过期撤单（Q12）：在途/锁超时/状态已变 → 跳过返回 false（不失败整次日切，幂等重跑兜底）；
     * CAS 重试耗尽仍抛 5001（与第一轮一致）。成功 → 状态 6、type 3、operator 空。
     */
    public boolean expireOne(String orderNo, String stockCode) {
        Lock lock = lockProvider.stockLock(stockCode);
        boolean acquired;
        try {
            acquired = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("过期撤单加锁被中断, 跳过待重跑, orderNo={}", orderNo);
            return false;
        } catch (RuntimeException e) {
            log.warn("过期撤单加锁故障, 跳过待重跑, orderNo={}", orderNo);
            return false;
        }
        if (!acquired) {
            log.warn("过期撤单加锁超时, 跳过待重跑, orderNo={}", orderNo);
            return false;
        }
        try {
            for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
                try {
                    Integer result = transactionTemplate.execute(status -> {
                        TradeOrder cur = tradeOrderMapper.selectOne(byOrderNo(orderNo));
                        if (cur == null || !isOpen(cur)) {
                            return 0;
                        }
                        if (dealRecordMapper.sumDealQtyByOrderNo(orderNo) != cur.getMatchedQty()) {
                            return -1;
                        }
                        TradeOrder update = unfreezeAndBuildUpdate(cur, "日切过期撤单");
                        if (!orderService.casTransit(cur, OrderStateMachine.EXPIRED, update, CHANGE_EXPIRE, null)) {
                            throw new BizException(ErrorCode.SYSTEM_BUSY, "订单CAS冲突，待重试");
                        }
                        return 1;
                    });
                    if (result != null && result == 1) {
                        matchEngine.bookOf(stockCode).remove(orderNo);
                        return true;
                    }
                    if (result != null && result == -1) {
                        log.warn("过期撤单遇在途记账, 跳过待重跑, orderNo={}", orderNo);
                    }
                    return false;
                } catch (BizException e) {
                    if (e.getErrorCode() == ErrorCode.SYSTEM_BUSY) {
                        continue;
                    }
                    throw e;
                }
            }
            throw new BizException(ErrorCode.SYSTEM_BUSY, "日切撤单并发冲突: " + orderNo);
        } finally {
            lock.unlock();
        }
    }

    private void assertNoInFlight(TradeOrder order) {
        long recorded = dealRecordMapper.sumDealQtyByOrderNo(order.getOrderNo());
        if (recorded != order.getMatchedQty()) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "存在在途记账，请稍后重试撤单");
        }
    }

    /** 解冻剩余精确值（买按 frozen_amount C4 / 卖按 frozen_qty C5），并构造冻结清零的撤单更新。 */
    private TradeOrder unfreezeAndBuildUpdate(TradeOrder order, String rejectReason) {
        if (order.getDirection() == DIRECTION_BUY) {
            if (order.getFrozenAmount().signum() > 0) {
                accountService.unfreeze(order.getUserId(), order.getFrozenAmount(),
                        BizNoGenerator.cancelFund(order.getOrderNo()), order.getOrderNo());
            }
        } else if (order.getFrozenQty() > 0) {
            positionService.unfreeze(order.getUserId(), order.getStockCode(), order.getFrozenQty(),
                    BizNoGenerator.cancelPosition(order.getOrderNo()), order.getOrderNo());
        }
        TradeOrder update = new TradeOrder();
        update.setRejectReason(rejectReason);
        update.setFrozenAmount(BigDecimal.ZERO);
        update.setFrozenQty(0L);
        return update;
    }

    private static LambdaQueryWrapper<TradeOrder> byOrderNo(String orderNo) {
        return new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo);
    }

    private static boolean isOpen(TradeOrder order) {
        return order.getStatus() == OrderStateMachine.PENDING
                || order.getStatus() == OrderStateMachine.PARTIAL_FILLED;
    }
}