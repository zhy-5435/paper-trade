package com.stocksim.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.account.entity.FundFlow;
import com.stocksim.account.mapper.FundFlowMapper;
import com.stocksim.common.BizNoGenerator;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.order.dto.CreateOrderRequest;
import com.stocksim.order.dto.OrderResponse;
import com.stocksim.order.service.OrderService;
import com.stocksim.position.service.PositionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 异步记账链路集成测试（走真实 broker + MySQL + Redis）。
 * 默认跳过，仅在 -Dstocksim.mq.it=true 时运行（DoD 冒烟阶段手动触发）。
 * 断言全部限定在本次自建订单/dealNo 上，可在冒烟后的脏库上运行；
 * 运行前应停止手动应用，避免同消费组 rebalance 抢消息。
 */
@SpringBootTest(properties = {
        "stocksim.deal.notify=mq",
        "stocksim.quote.refresh-interval-ms=3600000",
        "stocksim.mq.test-mode=false"
})
@EnabledIfSystemProperty(named = "stocksim.mq.it", matches = "true")
class MqAsyncBookingIntegrationTest {

    private static final Long BUYER = 2L;
    private static final Long SELLER = 3L;
    private static final String STOCK = "600000";
    private static final long QTY = 100L;
    private static final int FILLED = 3;

    @Autowired
    private OrderService orderService;
    @Autowired
    private DealRecordMapper dealRecordMapper;
    @Autowired
    private FundFlowMapper fundFlowMapper;
    @Autowired
    private PositionService positionService;
    @Autowired
    private DealMessageProducer producer;

    @Test
    void asyncDealBookedThenReplayIsIdempotent() throws InterruptedException {
        String suffix = String.valueOf(System.nanoTime());
        long sellerQtyBefore = positionService.getPosition(SELLER, STOCK).totalQty();

        OrderResponse sell = orderService.createOrder(SELLER,
                new CreateOrderRequest("it-sell-" + suffix, STOCK, 2, new BigDecimal("9.90"), QTY));
        OrderResponse buy = orderService.createOrder(BUYER,
                new CreateOrderRequest("it-buy-" + suffix, STOCK, 1, new BigDecimal("10.10"), QTY));

        DealRecord deal = awaitBookedDeal(buy.orderNo(), sell.orderNo());
        assertThat(deal).as("异步成交记账应在 20s 内完成（买卖均 FILLED + B5/S6 落库）").isNotNull();
        assertThat(deal.getSellerOrderNo()).isEqualTo(sell.orderNo());
        assertThat(deal.getDealQty()).isEqualTo(QTY);
        assertThat(deal.getDealPrice()).isEqualByComparingTo("9.90");
        assertThat(deal.getDealAmount()).isEqualByComparingTo("990.00");

        assertThat(orderService.getOrder(BUYER, buy.orderNo()).status()).isEqualTo(FILLED);
        assertThat(orderService.getOrder(BUYER, buy.orderNo()).matchedQty()).isEqualTo(QTY);
        assertThat(orderService.getOrder(SELLER, sell.orderNo()).status()).isEqualTo(FILLED);
        assertThat(orderService.getOrder(SELLER, sell.orderNo()).matchedQty()).isEqualTo(QTY);

        String dealNo = deal.getDealNo();
        assertThat(flowCount(BizNoGenerator.buyDealFund(dealNo))).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.buyFeeFund(dealNo))).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.sellDealFund(dealNo))).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.sellFeeFund(dealNo))).isEqualTo(1);
        assertThat(positionService.getPosition(SELLER, STOCK).totalQty()).isEqualTo(sellerQtyBefore - QTY);

        // 重放买/卖两条消息：幂等前置应 ACK，不产生重复流水/成交
        DealContext ctx = new DealContext(dealNo, deal.getDealSeq(), deal.getStockCode(), deal.getDealPrice(),
                deal.getDealQty(), deal.getBuyerOrderNo(), deal.getSellerOrderNo(),
                deal.getBuyerUserId(), deal.getSellerUserId());
        producer.resendOrderly(ctx, true);
        producer.resendOrderly(ctx, false);
        Thread.sleep(3000);

        assertThat(dealRecordMapper.countByDealNo(dealNo)).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.buyDealFund(dealNo))).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.buyFeeFund(dealNo))).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.sellDealFund(dealNo))).isEqualTo(1);
        assertThat(flowCount(BizNoGenerator.sellFeeFund(dealNo))).isEqualTo(1);
        assertThat(positionService.getPosition(SELLER, STOCK).totalQty()).isEqualTo(sellerQtyBefore - QTY);
    }

    private DealRecord awaitBookedDeal(String buyerOrderNo, String sellerOrderNo) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            List<DealRecord> deals = dealRecordMapper.selectList(
                    new LambdaQueryWrapper<DealRecord>().eq(DealRecord::getBuyerOrderNo, buyerOrderNo));
            if (deals.size() == 1) {
                DealRecord d = deals.get(0);
                if (sellerOrderNo.equals(d.getSellerOrderNo())
                        && orderService.getOrder(BUYER, buyerOrderNo).status() == FILLED
                        && orderService.getOrder(SELLER, sellerOrderNo).status() == FILLED
                        && flowCount(BizNoGenerator.buyDealFund(d.getDealNo())) == 1
                        && flowCount(BizNoGenerator.sellDealFund(d.getDealNo())) == 1) {
                    return d;
                }
            }
            Thread.sleep(500);
        }
        return null;
    }

    private long flowCount(String flowNo) {
        return fundFlowMapper.selectCount(new LambdaQueryWrapper<FundFlow>().eq(FundFlow::getFlowNo, flowNo));
    }
}