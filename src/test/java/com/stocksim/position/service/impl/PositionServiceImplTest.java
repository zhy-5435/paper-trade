package com.stocksim.position.service.impl;

import com.stocksim.common.BizException;
import com.stocksim.position.dto.PositionResponse;
import com.stocksim.position.entity.PositionFlow;
import com.stocksim.position.entity.UserPosition;
import com.stocksim.position.mapper.PositionFlowMapper;
import com.stocksim.position.mapper.UserPositionMapper;
import com.stocksim.stock.dto.QuoteResponse;
import com.stocksim.stock.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceImplTest {

    @Mock
    private UserPositionMapper userPositionMapper;
    @Mock
    private PositionFlowMapper positionFlowMapper;
    @Mock
    private QuoteService quoteService;
    @InjectMocks
    private PositionServiceImpl positionService;

    private UserPosition position(long total, long frozen, long today, String cost, String avg) {
        UserPosition p = new UserPosition();
        p.setId(10L);
        p.setUserId(3L);
        p.setStockCode("600000");
        p.setTotalQty(total);
        p.setFrozenQty(frozen);
        p.setTodayBuyQty(today);
        p.setCostAmount(new BigDecimal(cost));
        p.setAvgCostPrice(new BigDecimal(avg));
        p.setVersion(0);
        return p;
    }

    private QuoteResponse quote(String code, String latest) {
        return new QuoteResponse(code, null, null, null, new BigDecimal(latest),
                null, null, null, null, null, null, null, null);
    }

    @Test
    void 买入新建持仓_成本含费用加权() {
        when(userPositionMapper.selectOne(any())).thenReturn(null);
        when(userPositionMapper.insert(any(UserPosition.class))).thenReturn(1);

        positionService.addOnBuy(3L, "600000", 100, new BigDecimal("980.00"), new BigDecimal("5.00"),
                new BigDecimal("9.80"), "D1B1", "D1");

        ArgumentCaptor<UserPosition> insertCaptor = ArgumentCaptor.forClass(UserPosition.class);
        verify(userPositionMapper).insert(insertCaptor.capture());
        UserPosition inserted = insertCaptor.getValue();
        assertThat(inserted.getTotalQty()).isEqualTo(100);
        assertThat(inserted.getTodayBuyQty()).isEqualTo(100);
        assertThat(inserted.getCostAmount()).isEqualByComparingTo("985.00");
        assertThat(inserted.getAvgCostPrice()).isEqualByComparingTo("9.8500");

        ArgumentCaptor<PositionFlow> flowCaptor = ArgumentCaptor.forClass(PositionFlow.class);
        verify(positionFlowMapper).insert(flowCaptor.capture());
        PositionFlow flow = flowCaptor.getValue();
        assertThat(flow.getChangeType()).isEqualTo(1);
        assertThat(flow.getChangeQty()).isEqualTo(100);
        assertThat(flow.getChangeFrozen()).isEqualTo(0);
        assertThat(flow.getTotalQtyBefore()).isEqualTo(0);
        assertThat(flow.getTotalQtyAfter()).isEqualTo(100);
        assertThat(flow.getCostBefore()).isEqualByComparingTo("0.00");
        assertThat(flow.getCostAfter()).isEqualByComparingTo("985.00");
        assertThat(flow.getDealPrice()).isEqualByComparingTo("9.80");
    }

    @Test
    void 买入加仓_加权平均成本() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(100, 0, 100, "985.00", "9.8500"));
        when(userPositionMapper.updateById(any(UserPosition.class))).thenReturn(1);

        positionService.addOnBuy(3L, "600000", 100, new BigDecimal("1000.00"), new BigDecimal("5.00"),
                new BigDecimal("10.00"), "D2B1", "D2");

        ArgumentCaptor<UserPosition> updateCaptor = ArgumentCaptor.forClass(UserPosition.class);
        verify(userPositionMapper).updateById(updateCaptor.capture());
        UserPosition update = updateCaptor.getValue();
        assertThat(update.getTotalQty()).isEqualTo(200);
        assertThat(update.getTodayBuyQty()).isEqualTo(200);
        assertThat(update.getCostAmount()).isEqualByComparingTo("1990.00");
        assertThat(update.getAvgCostPrice()).isEqualByComparingTo("9.9500");
    }

    @Test
    void 卖出部分_均价结转并同步解冻() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(200, 100, 0, "1990.00", "9.9500"));
        when(userPositionMapper.updateById(any(UserPosition.class))).thenReturn(1);

        positionService.reduceOnSell(3L, "600000", 100, new BigDecimal("9.90"), "D1S2", "D1");

        ArgumentCaptor<UserPosition> updateCaptor = ArgumentCaptor.forClass(UserPosition.class);
        verify(userPositionMapper).updateById(updateCaptor.capture());
        UserPosition update = updateCaptor.getValue();
        assertThat(update.getTotalQty()).isEqualTo(100);
        assertThat(update.getFrozenQty()).isEqualTo(0);
        assertThat(update.getCostAmount()).isEqualByComparingTo("995.00");
        assertThat(update.getAvgCostPrice()).isEqualByComparingTo("9.9500");

        ArgumentCaptor<PositionFlow> flowCaptor = ArgumentCaptor.forClass(PositionFlow.class);
        verify(positionFlowMapper).insert(flowCaptor.capture());
        PositionFlow flow = flowCaptor.getValue();
        assertThat(flow.getChangeType()).isEqualTo(2);
        assertThat(flow.getChangeQty()).isEqualTo(-100);
        assertThat(flow.getChangeFrozen()).isEqualTo(-100);
        assertThat(flow.getCostAfter()).isEqualByComparingTo("995.00");
    }

    @Test
    void 卖出清仓_成本归零防残留() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(100, 100, 0, "995.00", "9.9500"));
        when(userPositionMapper.updateById(any(UserPosition.class))).thenReturn(1);

        positionService.reduceOnSell(3L, "600000", 100, new BigDecimal("10.00"), "D1S2", "D1");

        ArgumentCaptor<UserPosition> updateCaptor = ArgumentCaptor.forClass(UserPosition.class);
        verify(userPositionMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getTotalQty()).isEqualTo(0);
        assertThat(updateCaptor.getValue().getCostAmount()).isEqualByComparingTo("0.00");
        assertThat(updateCaptor.getValue().getAvgCostPrice()).isEqualByComparingTo("0.0000");

        ArgumentCaptor<PositionFlow> flowCaptor = ArgumentCaptor.forClass(PositionFlow.class);
        verify(positionFlowMapper).insert(flowCaptor.capture());
        assertThat(flowCaptor.getValue().getCostAfter()).isEqualByComparingTo("0.00");
    }

    @Test
    void 冻结_总量不变冻结增加() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(1000, 0, 0, "10000.00", "10.0000"));
        when(userPositionMapper.updateById(any(UserPosition.class))).thenReturn(1);

        positionService.freeze(3L, "600000", 200, "O1P4", "O1");

        ArgumentCaptor<UserPosition> updateCaptor = ArgumentCaptor.forClass(UserPosition.class);
        verify(userPositionMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getFrozenQty()).isEqualTo(200);
        assertThat(updateCaptor.getValue().getTotalQty()).isNull();

        ArgumentCaptor<PositionFlow> flowCaptor = ArgumentCaptor.forClass(PositionFlow.class);
        verify(positionFlowMapper).insert(flowCaptor.capture());
        PositionFlow flow = flowCaptor.getValue();
        assertThat(flow.getChangeType()).isEqualTo(4);
        assertThat(flow.getChangeQty()).isEqualTo(0);
        assertThat(flow.getChangeFrozen()).isEqualTo(200);
        assertThat(flow.getFrozenQtyAfter()).isEqualTo(200);
        assertThat(flow.getCostAfter()).isEqualByComparingTo("10000.00");
    }

    @Test
    void 冻结_T1当日买入不可卖_2003() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(100, 0, 100, "1000.00", "10.0000"));

        assertThatThrownBy(() -> positionService.freeze(3L, "600000", 1, "O1P4", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2003);
        verify(userPositionMapper, never()).updateById(any(UserPosition.class));
    }

    @Test
    void 冻结_已冻结部分排除_2003() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(1000, 800, 0, "10000.00", "10.0000"));

        assertThatThrownBy(() -> positionService.freeze(3L, "600000", 300, "O1P4", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(2003);
    }

    @Test
    void 解冻_冻结减少流水勾稽() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(1000, 200, 0, "10000.00", "10.0000"));
        when(userPositionMapper.updateById(any(UserPosition.class))).thenReturn(1);

        positionService.unfreeze(3L, "600000", 200, "O1C5", "O1");

        ArgumentCaptor<PositionFlow> flowCaptor = ArgumentCaptor.forClass(PositionFlow.class);
        verify(positionFlowMapper).insert(flowCaptor.capture());
        PositionFlow flow = flowCaptor.getValue();
        assertThat(flow.getChangeType()).isEqualTo(5);
        assertThat(flow.getChangeFrozen()).isEqualTo(-200);
        assertThat(flow.getFrozenQtyBefore()).isEqualTo(200);
        assertThat(flow.getFrozenQtyAfter()).isEqualTo(0);
    }

    @Test
    void CAS冲突_单次即抛不重试() {
        when(userPositionMapper.selectOne(any())).thenReturn(position(1000, 0, 0, "10000.00", "10.0000"));
        when(userPositionMapper.updateById(any(UserPosition.class))).thenReturn(0);

        assertThatThrownBy(() -> positionService.freeze(3L, "600000", 200, "O1P4", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(userPositionMapper, times(1)).updateById(any(UserPosition.class));
        verify(positionFlowMapper, never()).insert(any(PositionFlow.class));
    }

    @Test
    void 新建持仓唯一键冲突_抛5001由调用方重试() {
        when(userPositionMapper.selectOne(any())).thenReturn(null);
        when(userPositionMapper.insert(any(UserPosition.class))).thenThrow(new DuplicateKeyException("uk_user_stock"));

        assertThatThrownBy(() -> positionService.addOnBuy(3L, "600000", 100, new BigDecimal("980.00"),
                new BigDecimal("5.00"), new BigDecimal("9.80"), "D1B1", "D1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(5001);
        verify(positionFlowMapper, never()).insert(any(PositionFlow.class));
    }

    @Test
    void 持仓查询_实时市值浮盈盈亏比() {
        when(userPositionMapper.selectList(any())).thenReturn(List.of(position(1000, 0, 0, "10000.00", "10.0000")));
        when(quoteService.batchQuotes(any())).thenReturn(List.of(quote("600000", "10.50")));

        List<PositionResponse> result = positionService.listPositions(3L);

        assertThat(result).hasSize(1);
        PositionResponse resp = result.get(0);
        assertThat(resp.marketValue()).isEqualByComparingTo("10500.00");
        assertThat(resp.floatingPnl()).isEqualByComparingTo("500.00");
        assertThat(resp.pnlRatio()).isEqualByComparingTo("0.0500");
        assertThat(resp.availableQty()).isEqualTo(1000);
        assertThat(resp.latestPrice()).isEqualByComparingTo("10.50");
    }

    @Test
    void 持仓查询_可卖数量扣冻结与T1() {
        when(userPositionMapper.selectList(any())).thenReturn(List.of(position(1000, 200, 300, "10000.00", "10.0000")));
        when(quoteService.batchQuotes(any())).thenReturn(List.of(quote("600000", "10.00")));

        List<PositionResponse> result = positionService.listPositions(3L);

        assertThat(result.get(0).availableQty()).isEqualTo(500);
    }

    @Test
    void 总市值_多持仓求和() {
        UserPosition p1 = position(1000, 0, 0, "10000.00", "10.0000");
        UserPosition p2 = position(100, 0, 0, "500.00", "5.0000");
        p2.setStockCode("000007");
        when(userPositionMapper.selectList(any())).thenReturn(List.of(p1, p2));
        when(quoteService.batchQuotes(any())).thenReturn(List.of(quote("600000", "10.50"), quote("000007", "5.25")));

        BigDecimal total = positionService.totalMarketValue(3L);

        assertThat(total).isEqualByComparingTo("11025.00");
    }

    @Test
    void 非法股数拒绝() {
        assertThatThrownBy(() -> positionService.freeze(3L, "600000", 0, "O1P4", "O1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(1001);
    }
}