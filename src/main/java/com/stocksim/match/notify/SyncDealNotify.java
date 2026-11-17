package com.stocksim.match.notify;

import com.stocksim.match.engine.DealContext;
import com.stocksim.match.service.DealBookingService;
import com.stocksim.match.service.DealFactService;
import com.stocksim.order.entity.TradeOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同步回报（notify=sync）：一笔成交一个本地事务（T2），成交事实落库 + 买卖双方记账 + 日汇总 + 行情量额。
 * 成交事实复用 DealFactService、单侧记账复用 DealBookingService（与异步口径只有一份）；
 * 重放/重试被 uk_deal_no / uk_flow_no 拦截整体回滚（R1§6.5、R3）。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "sync")
public class SyncDealNotify implements DealNotify {

    private final DealBookingService bookingService;
    private final DealFactService dealFactService;

    public SyncDealNotify(DealBookingService bookingService, DealFactService dealFactService) {
        this.bookingService = bookingService;
        this.dealFactService = dealFactService;
    }

    @Override
    @Transactional
    public void onDeal(DealContext ctx) {
        TradeOrder buy = bookingService.requireOpenOrder(ctx.buyerOrderNo());
        TradeOrder sell = bookingService.requireOpenOrder(ctx.sellerOrderNo());
        dealFactService.recordDealFact(ctx);
        bookingService.bookBuy(ctx, buy);
        bookingService.bookSell(ctx, sell);
    }
}