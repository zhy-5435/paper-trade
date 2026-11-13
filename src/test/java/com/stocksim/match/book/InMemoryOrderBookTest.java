package com.stocksim.match.book;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderBookTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 13, 9, 30, 0);

    private BookOrder order(long id, String orderNo, int direction, String price, long qty, int plusSeconds) {
        return new BookOrder(id, orderNo, 1L, "600000", direction, new BigDecimal(price), qty,
                T0.plusSeconds(plusSeconds));
    }

    @Test
    void addIsIdempotentByOrderNo() {
        InMemoryOrderBook book = new InMemoryOrderBook();
        book.add(order(1, "O1", 1, "10.00", 100, 0));
        book.add(order(1, "O1", 1, "10.00", 100, 0));
        assertThat(book.size()).isEqualTo(1);
        assertThat(book.contains("O1")).isTrue();
    }

    @Test
    void bestBidIsHighestPriceAndBestAskIsLowest() {
        InMemoryOrderBook book = new InMemoryOrderBook();
        book.add(order(1, "B1", 1, "10.00", 100, 0));
        book.add(order(2, "B2", 1, "10.20", 100, 1));
        book.add(order(3, "S1", 2, "9.90", 100, 2));
        book.add(order(4, "S2", 2, "9.80", 100, 3));
        assertThat(book.bestBid().getOrderNo()).isEqualTo("B2");
        assertThat(book.bestAsk().getOrderNo()).isEqualTo("S2");
    }

    @Test
    void samePriceLevelIsFifo() {
        InMemoryOrderBook book = new InMemoryOrderBook();
        book.add(order(1, "B1", 1, "10.00", 100, 0));
        book.add(order(2, "B2", 1, "10.00", 100, 1));
        assertThat(book.bestBid().getOrderNo()).isEqualTo("B1");
        book.reduce(book.bestBid(), 100);
        assertThat(book.bestBid().getOrderNo()).isEqualTo("B2");
    }

    @Test
    void reducePartialKeepsOrderAndFullRemoves() {
        InMemoryOrderBook book = new InMemoryOrderBook();
        BookOrder bid = order(1, "B1", 1, "10.00", 200, 0);
        book.add(bid);
        book.reduce(bid, 100);
        assertThat(book.contains("B1")).isTrue();
        assertThat(bid.getRemainQty()).isEqualTo(100);
        book.reduce(bid, 100);
        assertThat(book.contains("B1")).isFalse();
        assertThat(book.bestBid()).isNull();
        assertThat(book.size()).isZero();
    }

    @Test
    void removeByOrderNo() {
        InMemoryOrderBook book = new InMemoryOrderBook();
        book.add(order(1, "S1", 2, "9.90", 100, 0));
        book.remove("S1");
        assertThat(book.contains("S1")).isFalse();
        assertThat(book.bestAsk()).isNull();
    }

    @Test
    void emptyBookReturnsNull() {
        InMemoryOrderBook book = new InMemoryOrderBook();
        assertThat(book.bestBid()).isNull();
        assertThat(book.bestAsk()).isNull();
        assertThat(book.size()).isZero();
    }
}