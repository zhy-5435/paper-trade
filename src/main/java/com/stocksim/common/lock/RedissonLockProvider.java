package com.stocksim.common.lock;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;

/**
 * Redisson 实现：getLock 不设 leaseTime，由看门狗自动续期（Q2）。
 * 返回的 RLock 即 java.util.concurrent.locks.Lock，业务侧无 Redisson API。
 */
@Component
public class RedissonLockProvider implements LockProvider {

    private final RedissonClient redisson;

    public RedissonLockProvider(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public Lock stockLock(String stockCode) {
        return redisson.getLock("lock:stock:" + stockCode);
    }

    @Override
    public Lock accountLock(Long userId) {
        return redisson.getLock("lock:account:" + userId);
    }

    @Override
    public Lock settlementLeaderLock() {
        return redisson.getLock("lock:settlement:leader");
    }
}