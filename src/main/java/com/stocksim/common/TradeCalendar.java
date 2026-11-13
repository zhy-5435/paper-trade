package com.stocksim.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TradeCalendar {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TradeCalendar() {
    }

    public static boolean isTradingDay(LocalDate date) {
        DayOfWeek week = date.getDayOfWeek();
        return week != DayOfWeek.SATURDAY && week != DayOfWeek.SUNDAY;
    }

    public static LocalDate nextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public static LocalDate currentTradingDay() {
        LocalDate today = LocalDate.now(ZONE);
        return isTradingDay(today) ? today : nextTradingDay(today);
    }
}