package com.stocksim.order.util;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;

import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {

    public static final int PENDING = 1;
    public static final int PARTIAL_FILLED = 2;
    public static final int FILLED = 3;
    public static final int CANCELED = 4;
    public static final int PARTIAL_CANCELED = 5;
    public static final int EXPIRED = 6;

    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            PENDING, Set.of(PARTIAL_FILLED, FILLED, CANCELED, EXPIRED),
            PARTIAL_FILLED, Set.of(FILLED, PARTIAL_CANCELED, EXPIRED));

    private OrderStateMachine() {
    }

    public static boolean canTransit(int from, int to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransit(int from, int to) {
        if (!canTransit(from, to)) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "非法订单状态迁移: " + from + "→" + to);
        }
    }
}