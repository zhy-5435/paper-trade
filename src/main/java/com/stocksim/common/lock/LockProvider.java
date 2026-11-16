package com.stocksim.common.lock;

import java.util.concurrent.locks.Lock;

/**
 * 分布式锁抽象：业务侧只见 {@link Lock}，不见 Redisson API（02§八演进红线）。
 * 三类 key：lock:stock:{code} / lock:account:{userId} / lock:settlement:leader。M2 接入撮合/账户/清算。
 */
public interface LockProvider {

    Lock stockLock(String stockCode);

    Lock accountLock(Long userId);

    Lock settlementLeaderLock();
}