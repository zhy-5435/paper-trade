package com.stocksim.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.PageResult;
import com.stocksim.common.TradeCalendar;
import com.stocksim.stock.dto.CreateStockRequest;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.dto.UpdateStockRequest;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.entity.StockQuote;
import com.stocksim.stock.mapper.StockInfoMapper;
import com.stocksim.stock.mapper.StockQuoteMapper;
import com.stocksim.stock.service.StockService;
import com.stocksim.stock.util.PriceLimits;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StockServiceImpl implements StockService {

    private static final int MAX_CAS_RETRY = 3;

    private final StockInfoMapper stockInfoMapper;
    private final StockQuoteMapper stockQuoteMapper;

    public StockServiceImpl(StockInfoMapper stockInfoMapper, StockQuoteMapper stockQuoteMapper) {
        this.stockInfoMapper = stockInfoMapper;
        this.stockQuoteMapper = stockQuoteMapper;
    }

    @Override
    public PageResult<StockResponse> listStocks(long page, long size) {
        Page<StockInfo> result = stockInfoMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<StockInfo>().orderByAsc(StockInfo::getStockCode));
        List<StockResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public StockResponse getStock(String stockCode) {
        return toResponse(requireStock(stockCode));
    }

    @Override
    @Transactional
    public StockResponse createStock(CreateStockRequest request) {
        Long count = stockInfoMapper.selectCount(
                new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getStockCode, request.stockCode()));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "股票代码已存在");
        }
        StockInfo stock = new StockInfo();
        stock.setStockCode(request.stockCode());
        stock.setStockName(request.stockName());
        stock.setMarket(request.market());
        stock.setBoardType(request.boardType());
        stock.setIssuePrice(request.issuePrice());
        stock.setPriceLimitRatio(request.priceLimitRatio());
        stock.setLotSize(request.lotSize() == null ? 100 : request.lotSize());
        stock.setStatus(1);
        stock.setListDate(request.listDate());
        try {
            stockInfoMapper.insert(stock);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "股票代码已存在");
        }
        initQuote(stock);
        return toResponse(stock);
    }

    @Override
    @Transactional
    public StockResponse updateStock(UpdateStockRequest request) {
        StockInfo stock = requireStock(request.stockCode());
        StockInfo update = new StockInfo();
        update.setId(stock.getId());
        update.setVersion(stock.getVersion());
        update.setStockName(request.stockName());
        update.setMarket(request.market());
        update.setBoardType(request.boardType());
        update.setPriceLimitRatio(request.priceLimitRatio());
        update.setLotSize(request.lotSize());
        update.setListDate(request.listDate());
        if (stockInfoMapper.updateById(update) == 0) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "股票并发更新冲突，请重试");
        }
        if (request.priceLimitRatio() != null
                && request.priceLimitRatio().compareTo(stock.getPriceLimitRatio()) != 0) {
            recomputeLimitPrices(request.stockCode(), request.priceLimitRatio());
        }
        return toResponse(requireStock(request.stockCode()));
    }

    @Override
    public void updateStatus(String stockCode, Integer status) {
        if (status == null || status < 0 || status > 2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法的股票状态");
        }
        StockInfo stock = requireStock(stockCode);
        StockInfo update = new StockInfo();
        update.setId(stock.getId());
        update.setVersion(stock.getVersion());
        update.setStatus(status);
        if (stockInfoMapper.updateById(update) == 0) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "股票并发更新冲突，请重试");
        }
    }

    private void initQuote(StockInfo stock) {
        StockQuote quote = new StockQuote();
        quote.setStockCode(stock.getStockCode());
        quote.setTradeDate(TradeCalendar.currentTradingDay());
        quote.setPreClosePrice(stock.getIssuePrice());
        quote.setLatestPrice(stock.getIssuePrice());
        quote.setLimitUpPrice(PriceLimits.up(stock.getIssuePrice(), stock.getPriceLimitRatio()));
        quote.setLimitDownPrice(PriceLimits.down(stock.getIssuePrice(), stock.getPriceLimitRatio()));
        quote.setTotalVolume(0L);
        quote.setTotalAmount(BigDecimal.ZERO);
        quote.setChangeRatio(BigDecimal.ZERO);
        quote.setQuoteTime(LocalDateTime.now(TradeCalendar.ZONE));
        stockQuoteMapper.insert(quote);
    }

    private void recomputeLimitPrices(String stockCode, BigDecimal ratio) {
        for (int attempt = 0; attempt < MAX_CAS_RETRY; attempt++) {
            StockQuote quote = stockQuoteMapper.selectOne(
                    new LambdaQueryWrapper<StockQuote>().eq(StockQuote::getStockCode, stockCode));
            if (quote == null) {
                return;
            }
            StockQuote update = new StockQuote();
            update.setId(quote.getId());
            update.setVersion(quote.getVersion());
            update.setLimitUpPrice(PriceLimits.up(quote.getPreClosePrice(), ratio));
            update.setLimitDownPrice(PriceLimits.down(quote.getPreClosePrice(), ratio));
            if (stockQuoteMapper.updateById(update) > 0) {
                return;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_BUSY, "行情涨跌停重算冲突，请重试");
    }

    private StockInfo requireStock(String stockCode) {
        StockInfo stock = stockInfoMapper.selectOne(
                new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getStockCode, stockCode));
        if (stock == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "股票不存在");
        }
        return stock;
    }

    private StockResponse toResponse(StockInfo stock) {
        return new StockResponse(stock.getStockCode(), stock.getStockName(), stock.getMarket(),
                stock.getBoardType(), stock.getIssuePrice(), stock.getPriceLimitRatio(),
                stock.getLotSize(), stock.getStatus(), stock.getListDate());
    }
}