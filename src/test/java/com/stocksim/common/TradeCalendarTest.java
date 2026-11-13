package com.stocksim.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TradeCalendarTest {

    @Test
    void weekdaysAreTradingDays() {
        assertThat(TradeCalendar.isTradingDay(LocalDate.of(2026, 11, 12))).isTrue();
        assertThat(TradeCalendar.isTradingDay(LocalDate.of(2026, 11, 13))).isTrue();
    }

    @Test
    void weekendIsNotTradingDay() {
        assertThat(TradeCalendar.isTradingDay(LocalDate.of(2026, 11, 14))).isFalse();
        assertThat(TradeCalendar.isTradingDay(LocalDate.of(2026, 11, 15))).isFalse();
    }

    @Test
    void fridayRollsToMonday() {
        assertThat(TradeCalendar.nextTradingDay(LocalDate.of(2026, 11, 13)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void weekdayRollsToNextDay() {
        assertThat(TradeCalendar.nextTradingDay(LocalDate.of(2026, 11, 12)))
                .isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void currentTradingDayNeverFallsOnWeekend() {
        assertThat(TradeCalendar.isTradingDay(TradeCalendar.currentTradingDay())).isTrue();
    }
}