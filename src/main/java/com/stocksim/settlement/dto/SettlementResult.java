package com.stocksim.settlement.dto;

import java.time.LocalDate;

public record SettlementResult(int expiredOrders, int cutQuotes, int clearedPositions,
                               LocalDate closeDate, LocalDate nextDate) {
}