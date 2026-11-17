package com.stocksim.match.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.account.entity.FundFlow;
import com.stocksim.account.mapper.FundFlowMapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.Amounts;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealOrderDaily;
import com.stocksim.match.mapper.DealOrderDailyMapper;
import com.stocksim.order.entity.TradeOrder;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.order.service.OrderService;
import com.stocksim.order.util.FeeCalculator;
import com.stocksim.order.util.OrderStateMachine;
import com.stocksim.position.service.PositionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单侧成交记账（R2规划§3.2/Q5）：订单 CAS + 账户/持仓 + 双侧流水 + 日汇总，同步/异步共用，口径只有一份。
 * bookBuy/bookSell 在调用方事务内执行（同步 T2 / 消费 T3b、T3s）；consumeBuy/consumeSell 为异步消费入口，
 * 幂等前置（本侧 B5/S6 流水存在 → 跳过）先于 requireOpenOrder，终态订单的已记账重放直接 ACK 不误入死信。
 */
@Service
public class DealBookingService {

    private final TradeOrderMapper tradeOrderMapper;
    private final DealOrderDailyMapper dealOrderDailyMapper;
    private final FundFlowMapper fundFlowMapper;
    private final OrderService orderService;
    private final AccountService accountService;
    private final PositionService positionService;
    private final FeeCalculator feeCalculator;

    public DealBookingService(TradeOrderMapper tradeOrderMapper, DealOrderDailyMapper dealOrderDailyMapper,
                              FundFlowMapper fundFlowMapper, OrderService orderService,
                              AccountService accountService, PositionService positionService,
                              FeeCalculator feeCalculator) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.dealOrderDailyMapper = dealOrderDailyMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.orderService = orderService;
        this.accountService = accountService;
        this.positionService = positionService;
        this.feeCalculator = feeCalculator;
    }

    /** 异步买方消费入口（T3b）：幂等前置 → requireOpenOrder → 单侧记账 */
    @Transactional
    public void consumeBuy(DealContext ctx) {
        if (alreadyBooked(ctx.dealNo(), true)) {
            return;
        }
        TradeOrder buy = requireOpenOrder(ctx.buyerOrderNo());
        bookBuy(ctx, buy);
    }

    /** 异步卖方消费入口（T3s）：幂等前置 → requireOpenOrder → 单侧记账 */
    @Transactional
    public void consumeSell(DealContext ctx) {
        if (alreadyBooked(ctx.dealNo(), false)) {
            return;
        }
        TradeOrder sell = requireOpenOrder(ctx.sellerOrderNo());
        bookSell(ctx, sell);
    }

    /** 幂等前置：本侧记账流水（买 B5 / 卖 S6）已存在即视为已记账（Q5） */
    public boolean alreadyBooked(String dealNo, boolean buy) {
        String flowNo = buy ? BizNoGenerator.buyDealFund(dealNo) : BizNoGenerator.sellDealFund(dealNo);
        return fundFlowMapper.selectCount(
                new LambdaQueryWrapper<FundFlow>().eq(FundFlow::getFlowNo, flowNo)) > 0;
    }

    /** 买方单侧记账（调用方事务内）：扣款 B5 + 佣金 B7 + 增仓 B1 + 订单 CAS + 日汇总 */
    public void bookBuy(DealContext ctx, TradeOrder buy) {
        long qty = ctx.dealQty();
        if (buy.getEntrustQty() - buy.getMatchedQty() < qty) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "订单剩余数量不足，待重试");
        }
        BigDecimal amount = Amounts.round2(ctx.dealPrice().multiply(BigDecimal.valueOf(qty)));
        BigDecimal commission = feeCalculator.commission(amount);
        BigDecimal unfreeze = buyUnfreeze(buy, qty);
        accountService.deduct(ctx.buyerUserId(), amount, unfreeze,
                BizNoGenerator.buyDealFund(ctx.dealNo()), ctx.dealNo());
        accountService.fee(ctx.buyerUserId(), commission,
                BizNoGenerator.buyFeeFund(ctx.dealNo()), ctx.dealNo());
        positionService.addOnBuy(ctx.buyerUserId(), ctx.stockCode(), qty, amount, commission, ctx.dealPrice(),
                BizNoGenerator.buyPosition(ctx.dealNo()), ctx.dealNo());
        transitOrder(buy, qty, amount, commission, unfreeze, true);
        upsertDaily(buy, qty, amount, commission);
    }

    /** 卖方单侧记账（调用方事务内）：减仓解冻 S2 + 入账 S6 + 佣金印花税 S7 + 订单 CAS + 日汇总 */
    public void bookSell(DealContext ctx, TradeOrder sell) {
        long qty = ctx.dealQty();
        if (sell.getEntrustQty() - sell.getMatchedQty() < qty) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "订单剩余数量不足，待重试");
        }
        BigDecimal amount = Amounts.round2(ctx.dealPrice().multiply(BigDecimal.valueOf(qty)));
        BigDecimal sellFee = feeCalculator.sellFee(amount);
        positionService.reduceOnSell(ctx.sellerUserId(), ctx.stockCode(), qty, ctx.dealPrice(),
                BizNoGenerator.sellPosition(ctx.dealNo()), ctx.dealNo());
        accountService.credit(ctx.sellerUserId(), amount,
                BizNoGenerator.sellDealFund(ctx.dealNo()), ctx.dealNo());
        accountService.fee(ctx.sellerUserId(), sellFee,
                BizNoGenerator.sellFeeFund(ctx.dealNo()), ctx.dealNo());
        transitOrder(sell, qty, amount, sellFee, BigDecimal.ZERO, false);
        upsertDaily(sell, qty, amount, sellFee);
    }

    public TradeOrder requireOpenOrder(String orderNo) {
        TradeOrder order = tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo));
        if (order == null || (order.getStatus() != OrderStateMachine.PENDING
                && order.getStatus() != OrderStateMachine.PARTIAL_FILLED)) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "订单状态已变化，待重试");
        }
        return order;
    }

    /** P2：部分成交按剩余冻结占比解冻（HALF_UP 到分），最后一笔成交解冻剩余精确值，保证零残留 */
    private static BigDecimal buyUnfreeze(TradeOrder buy, long qty) {
        long remainBefore = buy.getEntrustQty() - buy.getMatchedQty();
        if (qty == remainBefore) {
            return buy.getFrozenAmount();
        }
        return buy.getFrozenAmount().multiply(BigDecimal.valueOf(qty))
                .divide(BigDecimal.valueOf(remainBefore), 2, RoundingMode.HALF_UP);
    }

    private void transitOrder(TradeOrder order, long qty, BigDecimal amount, BigDecimal fee,
                              BigDecimal unfreeze, boolean buy) {
        boolean full = order.getMatchedQty() + qty == order.getEntrustQty();
        TradeOrder update = new TradeOrder();
        update.setMatchedQty(order.getMatchedQty() + qty);
        update.setMatchedAmount(order.getMatchedAmount().add(amount));
        update.setFeeAmount(order.getFeeAmount().add(fee));
        if (buy) {
            update.setFrozenAmount(order.getFrozenAmount().subtract(unfreeze));
        } else {
            update.setFrozenQty(order.getFrozenQty() - qty);
        }
        int toStatus = full ? OrderStateMachine.FILLED : OrderStateMachine.PARTIAL_FILLED;
        if (!orderService.casTransit(order, toStatus, update, 1, null)) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "订单CAS冲突，待重试");
        }
    }

    private void upsertDaily(TradeOrder order, long qty, BigDecimal amount, BigDecimal fee) {
        DealOrderDaily daily = dealOrderDailyMapper.selectOne(
                new LambdaQueryWrapper<DealOrderDaily>()
                        .eq(DealOrderDaily::getUserId, order.getUserId())
                        .eq(DealOrderDaily::getTradeDate, order.getTradeDate())
                        .eq(DealOrderDaily::getOrderNo, order.getOrderNo()));
        if (daily == null) {
            DealOrderDaily insert = new DealOrderDaily();
            insert.setTradeDate(order.getTradeDate());
            insert.setUserId(order.getUserId());
            insert.setOrderNo(order.getOrderNo());
            insert.setDealQty(qty);
            insert.setDealAmount(amount);
            insert.setFeeAmount(fee);
            dealOrderDailyMapper.insert(insert);
        } else {
            DealOrderDaily update = new DealOrderDaily();
            update.setId(daily.getId());
            update.setDealQty(daily.getDealQty() + qty);
            update.setDealAmount(daily.getDealAmount().add(amount));
            update.setFeeAmount(daily.getFeeAmount().add(fee));
            dealOrderDailyMapper.updateById(update);
        }
    }
}