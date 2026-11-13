package com.stocksim.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingSessionTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 12, hour, minute);
    }

    @Test
    void 交易时段边界包含() {
        assertTrue(TradingSession.inSession(at(9, 30)));
        assertTrue(TradingSession.inSession(at(11, 30)));
        assertTrue(TradingSession.inSession(at(13, 0)));
        assertTrue(TradingSession.inSession(at(15, 0)));
        assertTrue(TradingSession.inSession(at(10, 15)));
        assertTrue(TradingSession.inSession(at(14, 0)));
    }

    @Test
    void 非交易时段() {
        assertFalse(TradingSession.inSession(at(9, 29)));
        assertFalse(TradingSession.inSession(at(11, 31)));
        assertFalse(TradingSession.inSession(at(12, 0)));
        assertFalse(TradingSession.inSession(at(15, 1)));
        assertFalse(TradingSession.inSession(at(8, 0)));
    }

    @Test
    void 周末非交易日() {
        assertFalse(TradingSession.inSession(LocalDateTime.of(2026, 11, 14, 10, 0)));
        assertFalse(TradingSession.inSession(LocalDateTime.of(2026, 11, 15, 14, 0)));
    }
}