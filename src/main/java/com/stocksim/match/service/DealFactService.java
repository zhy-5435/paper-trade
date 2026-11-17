package com.stocksim.match.service;

import com.stocksim.common.Amounts;
import com.stocksim.common.TradeCalendar;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.stock.service.QuoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交事实落库（R2规划 Q4 的 T2'）：deal_record + 行情量额，幂等。
 * 不变量：deal_record 落库即成交事实，盘口扣减与 dealSeq 推进以本事务提交为准，不以消息发送结果为准。
 * 同步回报与 MQ BUY 本地执行器共用，落库口径只有一份；已存在则跳过插入与量额累加（防重复累加行情）。
 */
@Service
public class DealFactService {

    private final DealRecordMapper dealRecordMapper;
    private final QuoteService quoteService;

    public DealFactService(DealRecordMapper dealRecordMapper, QuoteService quoteService) {
        this.dealRecordMapper = dealRecordMapper;
        this.quoteService = quoteService;
    }

    /** 幂等落成交事实：已存在返回 false（跳过），新落库返回 true。调用方事务内执行（同步 T2 / BUY 执行器 T2'）。 */
    @Transactional
    public boolean recordDealFact(DealContext ctx) {
        if (exists(ctx.dealNo())) {
            return false;
        }
        long qty = ctx.dealQty();
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
        // P4：行情量额由实际成交累加，与 deal_record 可勾稽
        quoteService.accumulateDeal(ctx.stockCode(), qty, amount);
        return true;
    }

    /** 回查/失败归类只查 DB 不查内存（Q4）：deal_record by deal_no 存在性 */
    public boolean exists(String dealNo) {
        return dealRecordMapper.countByDealNo(dealNo) > 0;
    }
}