package com.stocksim.match.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合全局序号：单机 AtomicLong，启动取 DB MAX(deal_seq)（R1规划§6.7）。
 * next() 预分配，记账失败留空洞可接受；commit() 推进已提交水位。
 */
@Component
public class DealSeq {

    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicLong committed = new AtomicLong(0);

    public void init(long maxFromDb) {
        sequence.set(maxFromDb);
        committed.set(maxFromDb);
    }

    public long next() {
        return sequence.incrementAndGet();
    }

    public void commit(long seq) {
        committed.accumulateAndGet(seq, Math::max);
    }

    public long current() {
        return sequence.get();
    }

    public long committed() {
        return committed.get();
    }
}