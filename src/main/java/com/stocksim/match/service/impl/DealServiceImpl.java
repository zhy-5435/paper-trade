package com.stocksim.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stocksim.common.PageResult;
import com.stocksim.match.dto.DealResponse;
import com.stocksim.match.entity.DealRecord;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.match.service.DealService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DealServiceImpl implements DealService {

    private final DealRecordMapper dealRecordMapper;

    public DealServiceImpl(DealRecordMapper dealRecordMapper) {
        this.dealRecordMapper = dealRecordMapper;
    }

    @Override
    public PageResult<DealResponse> listDeals(Long userId, long page, long size, String stockCode) {
        Page<DealRecord> result = dealRecordMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<DealRecord>()
                        .and(w -> w.eq(DealRecord::getBuyerUserId, userId)
                                .or().eq(DealRecord::getSellerUserId, userId))
                        .eq(stockCode != null && !stockCode.isBlank(), DealRecord::getStockCode, stockCode)
                        .orderByDesc(DealRecord::getDealTime)
                        .orderByDesc(DealRecord::getDealSeq));
        List<DealResponse> records = result.getRecords().stream()
                .map(deal -> toResponse(deal, userId)).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private static DealResponse toResponse(DealRecord deal, Long userId) {
        boolean buy = deal.getBuyerUserId().equals(userId);
        return new DealResponse(deal.getDealNo(), deal.getStockCode(), buy ? 1 : 2,
                buy ? deal.getBuyerOrderNo() : deal.getSellerOrderNo(),
                deal.getDealPrice(), deal.getDealQty(), deal.getDealAmount(), deal.getDealSeq(), deal.getDealTime());
    }
}