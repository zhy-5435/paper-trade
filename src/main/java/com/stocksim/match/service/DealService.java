package com.stocksim.match.service;

import com.stocksim.common.PageResult;
import com.stocksim.match.dto.DealResponse;

public interface DealService {

    PageResult<DealResponse> listDeals(Long userId, long page, long size, String stockCode);
}