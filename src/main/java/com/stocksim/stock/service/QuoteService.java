package com.stocksim.stock.service;

import com.stocksim.stock.dto.QuoteResponse;

import java.math.BigDecimal;
import java.util.List;

public interface QuoteService {

    QuoteResponse getQuote(String stockCode);

    List<QuoteResponse> batchQuotes(List<String> codes);

    int refreshAll(boolean ignoreSession);

    /** P4：成交回报同事务累加行情量额，CAS 冲突抛 SYSTEM_BUSY 由撮合引擎整笔重试 */
    void accumulateDeal(String stockCode, long qty, BigDecimal amount);
}