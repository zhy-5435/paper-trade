package com.stocksim.position.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stocksim.common.Amounts;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.PageResult;
import com.stocksim.position.dto.PositionFlowResponse;
import com.stocksim.position.dto.PositionResponse;
import com.stocksim.position.entity.PositionFlow;
import com.stocksim.position.entity.UserPosition;
import com.stocksim.position.mapper.PositionFlowMapper;
import com.stocksim.position.mapper.UserPositionMapper;
import com.stocksim.position.service.PositionService;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.service.QuoteService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PositionServiceImpl implements PositionService {

    private final UserPositionMapper userPositionMapper;
    private final PositionFlowMapper positionFlowMapper;
    private final QuoteService quoteService;

    public PositionServiceImpl(UserPositionMapper userPositionMapper, PositionFlowMapper positionFlowMapper,
                               QuoteService quoteService) {
        this.userPositionMapper = userPositionMapper;
        this.positionFlowMapper = positionFlowMapper;
        this.quoteService = quoteService;
    }

    @Override
    public List<PositionResponse> listPositions(Long userId) {
        return computePositions(userId);
    }

    @Override
    public PositionResponse getPosition(Long userId, String stockCode) {
        UserPosition position = findByUserStock(userId, stockCode);
        if (position == null) {
            return null;
        }
        List<QuoteResponse> quotes = quoteService.batchQuotes(List.of(stockCode));
        return toResponse(position, quotes.isEmpty() ? null : quotes.get(0));
    }

    @Override
    public PageResult<PositionFlowResponse> listFlows(Long userId, long page, long size, String stockCode) {
        Page<PositionFlow> result = positionFlowMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<PositionFlow>()
                        .eq(PositionFlow::getUserId, userId)
                        .eq(stockCode != null && !stockCode.isBlank(), PositionFlow::getStockCode, stockCode)
                        .orderByDesc(PositionFlow::getCreateTime)
                        .orderByDesc(PositionFlow::getId));
        List<PositionFlowResponse> records = result.getRecords().stream().map(this::toFlowResponse).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public BigDecimal totalMarketValue(Long userId) {
        return computePositions(userId).stream()
                .map(PositionResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional
    public void freeze(Long userId, String stockCode, long qty, String flowNo, String bizNo) {
        requirePositiveQty(qty);
        UserPosition position = requirePosition(userId, stockCode);
        if (availableQty(position) < qty) {
            throw new BizException(ErrorCode.POSITION_NOT_ENOUGH, "可卖持仓不足");
        }
        UserPosition update = new UserPosition();
        update.setId(position.getId());
        update.setVersion(position.getVersion());
        update.setFrozenQty(position.getFrozenQty() + qty);
        casUpdate(update);
        positionFlowMapper.insert(buildFlow(position, 4, 0L, qty, null, position.getCostAmount(), flowNo, bizNo));
    }

    @Override
    @Transactional
    public void unfreeze(Long userId, String stockCode, long qty, String flowNo, String bizNo) {
        requirePositiveQty(qty);
        UserPosition position = requirePosition(userId, stockCode);
        UserPosition update = new UserPosition();
        update.setId(position.getId());
        update.setVersion(position.getVersion());
        update.setFrozenQty(position.getFrozenQty() - qty);
        casUpdate(update);
        positionFlowMapper.insert(buildFlow(position, 5, 0L, -qty, null, position.getCostAmount(), flowNo, bizNo));
    }

    @Override
    @Transactional
    public void addOnBuy(Long userId, String stockCode, long qty, BigDecimal dealAmount, BigDecimal fee,
                         BigDecimal dealPrice, String flowNo, String bizNo) {
        requirePositiveQty(qty);
        if (dealAmount == null || dealAmount.signum() < 0 || fee == null || fee.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成交金额或费用非法");
        }
        UserPosition before = findByUserStock(userId, stockCode);
        boolean isNew = before == null;
        if (isNew) {
            before = zeroPosition(userId, stockCode);
        }
        long newTotal = before.getTotalQty() + qty;
        BigDecimal newCost = before.getCostAmount().add(dealAmount).add(fee);
        BigDecimal newAvg = newCost.divide(BigDecimal.valueOf(newTotal), 4, RoundingMode.HALF_UP);
        long newToday = before.getTodayBuyQty() + qty;
        if (isNew) {
            UserPosition insert = new UserPosition();
            insert.setUserId(userId);
            insert.setStockCode(stockCode);
            insert.setTotalQty(newTotal);
            insert.setFrozenQty(0L);
            insert.setTodayBuyQty(newToday);
            insert.setCostAmount(newCost);
            insert.setAvgCostPrice(newAvg);
            try {
                userPositionMapper.insert(insert);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.SYSTEM_BUSY, "持仓并发冲突，请重试");
            }
        } else {
            UserPosition update = new UserPosition();
            update.setId(before.getId());
            update.setVersion(before.getVersion());
            update.setTotalQty(newTotal);
            update.setTodayBuyQty(newToday);
            update.setCostAmount(newCost);
            update.setAvgCostPrice(newAvg);
            casUpdate(update);
        }
        positionFlowMapper.insert(buildFlow(before, 1, qty, 0L, dealPrice, newCost, flowNo, bizNo));
    }

    @Override
    @Transactional
    public void reduceOnSell(Long userId, String stockCode, long qty, BigDecimal dealPrice, String flowNo, String bizNo) {
        requirePositiveQty(qty);
        UserPosition before = requirePosition(userId, stockCode);
        long newTotal = before.getTotalQty() - qty;
        long newFrozen = before.getFrozenQty() - qty;
        BigDecimal newCost;
        BigDecimal newAvg;
        if (newTotal == 0) {
            // 清仓归零，避免均价舍入残留成本
            newCost = BigDecimal.ZERO;
            newAvg = BigDecimal.ZERO;
        } else {
            newCost = Amounts.round2(before.getCostAmount()
                    .subtract(before.getAvgCostPrice().multiply(BigDecimal.valueOf(qty))));
            newAvg = before.getAvgCostPrice();
        }
        UserPosition update = new UserPosition();
        update.setId(before.getId());
        update.setVersion(before.getVersion());
        update.setTotalQty(newTotal);
        update.setFrozenQty(newFrozen);
        update.setCostAmount(newCost);
        update.setAvgCostPrice(newAvg);
        casUpdate(update);
        positionFlowMapper.insert(buildFlow(before, 2, -qty, -qty, dealPrice, newCost, flowNo, bizNo));
    }

    private List<PositionResponse> computePositions(Long userId) {
        List<UserPosition> positions = userPositionMapper.selectList(
                new LambdaQueryWrapper<UserPosition>()
                        .eq(UserPosition::getUserId, userId)
                        .orderByAsc(UserPosition::getStockCode));
        if (positions.isEmpty()) {
            return List.of();
        }
        List<String> codes = positions.stream().map(UserPosition::getStockCode).toList();
        Map<String, QuoteResponse> quotes = quoteService.batchQuotes(codes).stream()
                .collect(Collectors.toMap(QuoteResponse::stockCode, Function.identity()));
        return positions.stream().map(p -> toResponse(p, quotes.get(p.getStockCode()))).toList();
    }

    private PositionResponse toResponse(UserPosition position, QuoteResponse quote) {
        BigDecimal latest = quote != null ? quote.latestPrice() : position.getAvgCostPrice();
        BigDecimal marketValue = Amounts.round2(latest.multiply(BigDecimal.valueOf(position.getTotalQty())));
        BigDecimal floatingPnl = marketValue.subtract(position.getCostAmount());
        BigDecimal pnlRatio = position.getCostAmount().signum() == 0 ? BigDecimal.ZERO
                : floatingPnl.divide(position.getCostAmount(), 4, RoundingMode.HALF_UP);
        return new PositionResponse(position.getStockCode(), position.getTotalQty(), position.getFrozenQty(),
                position.getTodayBuyQty(), availableQty(position), position.getCostAmount(),
                position.getAvgCostPrice(), latest, marketValue, floatingPnl, pnlRatio);
    }

    private void casUpdate(UserPosition update) {
        if (userPositionMapper.updateById(update) == 0) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "持仓并发冲突，请重试");
        }
    }

    private PositionFlow buildFlow(UserPosition before, int changeType, long changeQty, long changeFrozen,
                                   BigDecimal dealPrice, BigDecimal costAfter, String flowNo, String bizNo) {
        PositionFlow flow = new PositionFlow();
        flow.setFlowNo(flowNo);
        flow.setUserId(before.getUserId());
        flow.setStockCode(before.getStockCode());
        flow.setChangeType(changeType);
        flow.setChangeQty(changeQty);
        flow.setChangeFrozen(changeFrozen);
        flow.setDealPrice(dealPrice);
        flow.setTotalQtyBefore(before.getTotalQty());
        flow.setTotalQtyAfter(before.getTotalQty() + changeQty);
        flow.setFrozenQtyBefore(before.getFrozenQty());
        flow.setFrozenQtyAfter(before.getFrozenQty() + changeFrozen);
        flow.setCostBefore(before.getCostAmount());
        flow.setCostAfter(costAfter);
        flow.setBizNo(bizNo);
        return flow;
    }

    private UserPosition requirePosition(Long userId, String stockCode) {
        UserPosition position = findByUserStock(userId, stockCode);
        if (position == null) {
            throw new BizException(ErrorCode.POSITION_NOT_ENOUGH, "持仓不存在");
        }
        return position;
    }

    private UserPosition findByUserStock(Long userId, String stockCode) {
        return userPositionMapper.selectOne(
                new LambdaQueryWrapper<UserPosition>()
                        .eq(UserPosition::getUserId, userId)
                        .eq(UserPosition::getStockCode, stockCode));
    }

    private static UserPosition zeroPosition(Long userId, String stockCode) {
        UserPosition position = new UserPosition();
        position.setUserId(userId);
        position.setStockCode(stockCode);
        position.setTotalQty(0L);
        position.setFrozenQty(0L);
        position.setTodayBuyQty(0L);
        position.setCostAmount(BigDecimal.ZERO);
        position.setAvgCostPrice(BigDecimal.ZERO);
        return position;
    }

    private static long availableQty(UserPosition position) {
        return position.getTotalQty() - position.getFrozenQty() - position.getTodayBuyQty();
    }

    private static void requirePositiveQty(long qty) {
        if (qty <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "股数必须大于0");
        }
    }

    private PositionFlowResponse toFlowResponse(PositionFlow flow) {
        return new PositionFlowResponse(flow.getFlowNo(), flow.getStockCode(), flow.getChangeType(),
                flow.getChangeQty(), flow.getChangeFrozen(), flow.getDealPrice(), flow.getTotalQtyBefore(),
                flow.getTotalQtyAfter(), flow.getFrozenQtyBefore(), flow.getFrozenQtyAfter(),
                flow.getCostBefore(), flow.getCostAfter(), flow.getBizNo(), flow.getCreateTime());
    }
}