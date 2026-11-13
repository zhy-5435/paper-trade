package com.stocksim.match.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.Amounts;
import com.stocksim.common.BizException;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.TradeCalendar;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 第一轮同步回报：一笔成交一个本地事务（T2），deal_record + 买卖双方记账 + 日汇总 + 行情量额。
 * 重放/重试被 uk_deal_no / uk_flow_no 拦截整体回滚（07§6.5、R3）。
 */
@Component
public class SyncDealNotify implements DealNotify {

    private final TradeOrderMapper tradeOrderMapper;
    private final DealRecordMapper dealRecordMapper;
    private final DealOrderDailyMapper dealOrderDailyMapper;
    private final OrderService orderService;
    private final AccountService accountService;
    private final PositionService positionService;
    private final QuoteService quoteService;
    private final FeeCalculator feeCalculator;

    public SyncDealNotify(TradeOrderMapper tradeOrderMapper, DealRecordMapper dealRecordMapper,
                          DealOrderDailyMapper dealOrderDailyMapper, OrderService orderService,
                          AccountService accountService, PositionService positionService,
                          QuoteService quoteService, FeeCalculator feeCalculator) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.dealRecordMapper = dealRecordMapper;
        this.dealOrderDailyMapper = dealOrderDailyMapper;
        this.orderService = orderService;
        this.accountService = accountService;
        this.positionService = positionService;
        this.quoteService = quoteService;
        this.feeCalculator = feeCalculator;
    }

    @Override
    @Transactional
    public void onDeal(DealContext ctx) {
        TradeOrder buy = requireOpenOrder(ctx.buyerOrderNo());
        TradeOrder sell = requireOpenOrder(ctx.sellerOrderNo());
        long qty = ctx.dealQty();
        if (buy.getEntrustQty() - buy.getMatchedQty() < qty
                || sell.getEntrustQty() - sell.getMatchedQty() < qty) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "订单剩余数量不足，待重试");
        }
        BigDecimal amount = Amounts.round2(ctx.dealPrice().multiply(BigDecimal.valueOf(qty)));

        DealRecord record = new DealRecord();
        record.setDealNo(ctx.dealNo());
        record.setStockCode(ctx.stockCode());
        record.setDealPrice(ctx.dealPrice());
        record.setDealQty(qty);
        record.setDealAmount(amount);
        record.setBuyerOrderNo(ctx.buyerOrderNo());
        record.setSellerOrderNo(ctx.sellerOrderNo());
        record.setBuyerUserId(ctx.buyerUserId());
        record.setSellerUserId(ctx.sellerUserId());
        record.setDealSeq(ctx.dealSeq());
        record.setDealTime(LocalDateTime.now(TradeCalendar.ZONE));
        dealRecordMapper.insert(record);

        BigDecimal commission = feeCalculator.commission(amount);
        BigDecimal unfreeze = buyUnfreeze(buy, qty);
        accountService.deduct(ctx.buyerUserId(), amount, unfreeze,
                BizNoGenerator.buyDealFund(ctx.dealNo()), ctx.dealNo());
        accountService.fee(ctx.buyerUserId(), commission,
                BizNoGenerator.buyFeeFund(ctx.dealNo()), ctx.dealNo());
        positionService.addOnBuy(ctx.buyerUserId(), ctx.stockCode(), qty, amount, commission, ctx.dealPrice(),
                BizNoGenerator.buyPosition(ctx.dealNo()), ctx.dealNo());
        transitOrder(buy, qty, amount, commission, unfreeze, true);

        BigDecimal sellFee = feeCalculator.sellFee(amount);
        positionService.reduceOnSell(ctx.sellerUserId(), ctx.stockCode(), qty, ctx.dealPrice(),
                BizNoGenerator.sellPosition(ctx.dealNo()), ctx.dealNo());
        accountService.credit(ctx.sellerUserId(), amount,
                BizNoGenerator.sellDealFund(ctx.dealNo()), ctx.dealNo());
        accountService.fee(ctx.sellerUserId(), sellFee,
                BizNoGenerator.sellFeeFund(ctx.dealNo()), ctx.dealNo());
        transitOrder(sell, qty, amount, sellFee, BigDecimal.ZERO, false);

        upsertDaily(buy, qty, amount, commission);
        upsertDaily(sell, qty, amount, sellFee);
        // P4：行情量额由实际成交累加，与 deal_record 可勾稽
        quoteService.accumulateDeal(ctx.stockCode(), qty, amount);
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
        if (!orderService.casTransit(order, toStatus, update)) {
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

    private TradeOrder requireOpenOrder(String orderNo) {
        TradeOrder order = tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo));
        if (order == null || (order.getStatus() != OrderStateMachine.PENDING
                && order.getStatus() != OrderStateMachine.PARTIAL_FILLED)) {
            throw new BizException(ErrorCode.MATCH_CONFLICT, "订单状态已变化，待重试");
        }
        return order;
    }
}