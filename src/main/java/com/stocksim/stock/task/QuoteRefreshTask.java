package com.stocksim.stock.task;

import com.stocksim.stock.service.QuoteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuoteRefreshTask {

    private final QuoteService quoteService;

    public QuoteRefreshTask(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Scheduled(fixedDelayString = "${stocksim.quote.refresh-interval-ms}")
    public void refresh() {
        quoteService.refreshAll(false);
    }
}