package com.stocksim.common;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class TradingSession {

    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private TradingSession() {
    }

    public static boolean inSession(LocalDateTime time) {
        if (!TradeCalendar.isTradingDay(time.toLocalDate())) {
            return false;
        }
        LocalTime t = time.toLocalTime();
        return (!t.isBefore(MORNING_OPEN) && !t.isAfter(MORNING_CLOSE))
                || (!t.isBefore(AFTERNOON_OPEN) && !t.isAfter(AFTERNOON_CLOSE));
    }
}