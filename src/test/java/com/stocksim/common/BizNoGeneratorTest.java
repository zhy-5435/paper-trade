package com.stocksim.common;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BizNoGeneratorTest {

    private final BizNoGenerator generator = new BizNoGenerator(new DefaultIdentifierGenerator(1, 1));

    @Test
    void businessNosUsePrefixAndFitColumnLength() {
        assertThat(generator.orderNo()).startsWith("O").hasSizeLessThanOrEqualTo(32);
        assertThat(generator.dealNo()).startsWith("D").hasSizeLessThanOrEqualTo(32);
        assertThat(generator.accountNo()).startsWith("A").hasSizeLessThanOrEqualTo(32);
        assertThat(generator.flowNo()).startsWith("F").hasSizeLessThanOrEqualTo(32);
    }

    @Test
    void generatedNosAreUnique() {
        Set<String> nos = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(nos.add(generator.orderNo())).isTrue();
        }
    }

    @Test
    void derivedFlowNosFollowConvention() {
        String orderNo = generator.orderNo();
        String dealNo = generator.dealNo();

        assertThat(BizNoGenerator.orderFreezeFund(orderNo)).isEqualTo(orderNo + "F3");
        assertThat(BizNoGenerator.orderFreezePosition(orderNo)).isEqualTo(orderNo + "P4");
        assertThat(BizNoGenerator.buyDealFund(dealNo)).isEqualTo(dealNo + "B5");
        assertThat(BizNoGenerator.buyFeeFund(dealNo)).isEqualTo(dealNo + "B7");
        assertThat(BizNoGenerator.buyPosition(dealNo)).isEqualTo(dealNo + "B1");
        assertThat(BizNoGenerator.sellDealFund(dealNo)).isEqualTo(dealNo + "S6");
        assertThat(BizNoGenerator.sellFeeFund(dealNo)).isEqualTo(dealNo + "S7");
        assertThat(BizNoGenerator.sellPosition(dealNo)).isEqualTo(dealNo + "S2");
        assertThat(BizNoGenerator.cancelFund(orderNo)).isEqualTo(orderNo + "C4");
        assertThat(BizNoGenerator.cancelPosition(orderNo)).isEqualTo(orderNo + "C5");
    }

    @Test
    void derivedFlowNosFitColumnLength() {
        String orderNo = generator.orderNo();
        String dealNo = generator.dealNo();
        assertThat(BizNoGenerator.orderFreezeFund(orderNo)).hasSizeLessThanOrEqualTo(32);
        assertThat(BizNoGenerator.cancelPosition(orderNo)).hasSizeLessThanOrEqualTo(32);
        assertThat(BizNoGenerator.sellFeeFund(dealNo)).hasSizeLessThanOrEqualTo(32);
    }
}