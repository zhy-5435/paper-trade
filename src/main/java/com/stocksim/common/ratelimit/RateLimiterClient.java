package com.stocksim.common.ratelimit;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 限流封装：Redisson RRateLimiter 收敛于此，业务侧无 Redisson API（02§八）。
 * 速率持久化在 Redis，须用 setRate 覆盖（trySetRate 不更新已存在速率，R16）。M5 切面接入，本轮空壳不接业务。
 */
@Component
public class RateLimiterClient {

    private final RedissonClient redisson;

    public RateLimiterClient(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void setRate(String key, long ratePerSecond) {
        RRateLimiter limiter = redisson.getRateLimiter(key);
        limiter.setRate(RateType.OVERALL, ratePerSecond, 1, RateIntervalUnit.SECONDS);
    }

    public boolean tryAcquire(String key) {
        return redisson.getRateLimiter(key).tryAcquire();
    }
}