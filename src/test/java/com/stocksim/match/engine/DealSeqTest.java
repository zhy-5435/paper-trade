package com.stocksim.match.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DealSeqTest {

    @Test
    void initFromDbMaxAndNextIsMonotonic() {
        DealSeq seq = new DealSeq();
        seq.init(100);
        assertThat(seq.current()).isEqualTo(100);
        assertThat(seq.committed()).isEqualTo(100);
        assertThat(seq.next()).isEqualTo(101);
        assertThat(seq.next()).isEqualTo(102);
        assertThat(seq.current()).isEqualTo(102);
    }

    @Test
    void commitAdvancesWatermarkToMax() {
        DealSeq seq = new DealSeq();
        seq.init(0);
        long s1 = seq.next();
        long s2 = seq.next();
        seq.commit(s2);
        seq.commit(s1);
        assertThat(seq.committed()).isEqualTo(s2);
    }

    @Test
    void failedDealLeavesGapButCommittedOnlyAdvancesOnSuccess() {
        DealSeq seq = new DealSeq();
        seq.init(0);
        long failed = seq.next();
        long ok = seq.next();
        seq.commit(ok);
        assertThat(seq.current()).isEqualTo(ok);
        assertThat(seq.committed()).isEqualTo(ok);
        assertThat(failed).isLessThan(ok);
    }
}