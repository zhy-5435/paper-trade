package com.stocksim.common.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁实现：仅测试用（Q11），不注册为生产 Bean。单 JVM 内按 key 复用 ReentrantLock，
 * 供纯 Mockito 单测注入 LockProvider 时保持确定性。
 */
public class LocalLockProvider implements LockProvider {

    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();

    @Override
    public Lock stockLock(String stockCode) {
        return locks.computeIfAbsent("lock:stock:" + stockCode, k -> new ReentrantLock());
    }

    @Override
    public Lock accountLock(Long userId) {
        return locks.computeIfAbsent("lock:account:" + userId, k -> new ReentrantLock());
    }

    @Override
    public Lock settlementLeaderLock() {
        return locks.computeIfAbsent("lock:settlement:leader", k -> new ReentrantLock());
    }
}