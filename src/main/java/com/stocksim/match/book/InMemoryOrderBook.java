package com.stocksim.match.book;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** 买单降序/卖单升序 TreeMap，同价 FIFO（07§6.6） */
public class InMemoryOrderBook implements OrderBook {

    private final NavigableMap<BigDecimal, ArrayDeque<BookOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, ArrayDeque<BookOrder>> asks = new TreeMap<>();
    private final Map<String, BookOrder> index = new HashMap<>();

    @Override
    public void add(BookOrder order) {
        if (index.containsKey(order.getOrderNo())) {
            return;
        }
        NavigableMap<BigDecimal, ArrayDeque<BookOrder>> side =
                order.getDirection() == 1 ? bids : asks;
        side.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).addLast(order);
        index.put(order.getOrderNo(), order);
    }

    @Override
    public BookOrder bestBid() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue().peekFirst();
    }

    @Override
    public BookOrder bestAsk() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue().peekFirst();
    }

    @Override
    public void reduce(BookOrder order, long qty) {
        order.setRemainQty(order.getRemainQty() - qty);
        if (order.getRemainQty() <= 0) {
            removeFromSide(order);
            index.remove(order.getOrderNo());
        }
    }

    @Override
    public void remove(String orderNo) {
        BookOrder order = index.remove(orderNo);
        if (order != null) {
            removeFromSide(order);
        }
    }

    @Override
    public boolean contains(String orderNo) {
        return index.containsKey(orderNo);
    }

    @Override
    public int size() {
        return index.size();
    }

    private void removeFromSide(BookOrder order) {
        NavigableMap<BigDecimal, ArrayDeque<BookOrder>> side =
                order.getDirection() == 1 ? bids : asks;
        ArrayDeque<BookOrder> level = side.get(order.getPrice());
        if (level != null) {
            level.remove(order);
            if (level.isEmpty()) {
                side.remove(order.getPrice());
            }
        }
    }
}