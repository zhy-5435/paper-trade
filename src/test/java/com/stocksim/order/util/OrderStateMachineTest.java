package com.stocksim.order.util;

import com.stocksim.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void 全部合法迁移() {
        int[][] legal = {{1, 2}, {1, 3}, {1, 4}, {1, 6}, {2, 3}, {2, 5}, {2, 6}};
        for (int[] pair : legal) {
            assertThat(OrderStateMachine.canTransit(pair[0], pair[1])).isTrue();
            OrderStateMachine.assertTransit(pair[0], pair[1]);
        }
    }

    @Test
    void 非法迁移拒绝() {
        int[][] illegal = {{3, 2}, {4, 1}, {1, 5}, {2, 4}, {3, 1}, {5, 3}, {6, 1}, {1, 1}, {2, 2}, {3, 4}};
        for (int[] pair : illegal) {
            assertThat(OrderStateMachine.canTransit(pair[0], pair[1])).isFalse();
            assertThatThrownBy(() -> OrderStateMachine.assertTransit(pair[0], pair[1]))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode().getCode())
                    .isEqualTo(5001);
        }
    }

    @Test
    void 终态无出边() {
        for (int terminal : new int[]{3, 4, 5, 6}) {
            for (int to = 1; to <= 6; to++) {
                assertThat(OrderStateMachine.canTransit(terminal, to)).isFalse();
            }
        }
    }
}