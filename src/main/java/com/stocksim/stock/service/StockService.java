package com.stocksim.stock.service;

import com.stocksim.common.PageResult;
import com.stocksim.stock.dto.CreateStockRequest;
import com.stocksim.stock.dto.StockResponse;
import com.stocksim.stock.dto.UpdateStockRequest;

public interface StockService {

    PageResult<StockResponse> listStocks(long page, long size);

    StockResponse getStock(String stockCode);

    StockResponse createStock(CreateStockRequest request);

    StockResponse updateStock(UpdateStockRequest request);

    void updateStatus(String stockCode, Integer status);
}