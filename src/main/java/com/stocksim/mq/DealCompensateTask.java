package com.stocksim.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stocksim.common.TradeCalendar;
import com.stocksim.common.properties.MqProperties;
import com.stocksim.match.engine.DealContext;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.match.service.DealBookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成交记账补偿任务（R2规划 Q7）：扫近 N 分钟 deal_record，按侧比对记账流水（买 B5/卖 S6），
 * 缺记账 → 按 order_no 分片键补发普通顺序消息（消费幂等兜底）。覆盖发送崩溃窗口、重试耗尽、死信三类异常。
 * 不用延迟消息（4.9.x 限制）；与消费幂等互为兜底，补发可同 dealNo。
 */
@Component
@ConditionalOnProperty(prefix = "stocksim.deal", name = "notify", havingValue = "mq", matchIfMissing = true)
public class DealCompensateTask {

    private static final Logger log = LoggerFactory.getLogger(DealCompensateTask.class);

    private final DealRecordMapper dealRecordMapper;
    private final DealBookingService bookingService;
    private final DealMessageProducer producer;
    private final MqProperties mqProperties;

    public DealCompensateTask(DealRecordMapper dealRecordMapper, DealBookingService bookingService,
                              DealMessageProducer producer, MqProperties mqProperties) {
        this.dealRecordMapper = dealRecordMapper;
        this.bookingService = bookingService;
        this.producer = producer;
        this.mqProperties = mqProperties;
    }

    @Scheduled(fixedDelayString = "${stocksim.mq.compensate-interval-ms:60000}")
    public void compensate() {
        LocalDateTime windowStart = LocalDateTime.now(TradeCalendar.ZONE)
                .minusMinutes(mqProperties.getCompensateWindowMinutes());
        List<DealRecord> recent = dealRecordMapper.selectList(
                new LambdaQueryWrapper<DealRecord>().ge(DealRecord::getDealTime, windowStart));
        int resent = 0;
        for (DealRecord record : recent) {
            DealContext ctx = toContext(record);
            if (!bookingService.alreadyBooked(record.getDealNo(), true)) {
                producer.resendOrderly(ctx, true);
                resent++;
            }
            if (!bookingService.alreadyBooked(record.getDealNo(), false)) {
                producer.resendOrderly(ctx, false);
                resent++;
            }
        }
        if (resent > 0) {
            log.warn("补偿补发 {} 条缺失记账消息, 窗口 {} 分钟, 扫描 deal_record {} 笔",
                    resent, mqProperties.getCompensateWindowMinutes(), recent.size());
        }
    }

    private static DealContext toContext(DealRecord r) {
        return new DealContext(r.getDealNo(), r.getDealSeq(), r.getStockCode(), r.getDealPrice(), r.getDealQty(),
                r.getBuyerOrderNo(), r.getSellerOrderNo(), r.getBuyerUserId(), r.getSellerUserId());
    }
}