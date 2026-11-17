package com.stocksim.common.ratelimit;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.context.UserContext;
import com.stocksim.common.properties.RateLimitProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面（Q8）：key=rl:{type}:{userId}，按用户维度 RateType.OVERALL。
 * 速率持久化在 Redis，每个 key 启动后首次使用时 setRate 覆盖写入（trySetRate 不更新已存在速率，R16），
 * 本地 Map 记已初始化；改阈值重启后首次使用即覆盖生效。tryAcquire 0 等待失败 → 1004。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterClient rateLimiterClient;
    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Boolean> initializedKeys = new ConcurrentHashMap<>();

    public RateLimitAspect(RateLimiterClient rateLimiterClient, RateLimitProperties properties) {
        this.rateLimiterClient = rateLimiterClient;
        this.properties = properties;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        if (!properties.isEnabled()) {
            return pjp.proceed();
        }
        Long userId = UserContext.userId();
        if (userId == null) {
            return pjp.proceed();
        }
        RateLimitType type = rateLimit.type();
        String key = "rl:" + type.name() + ":" + userId;
        long rate = type == RateLimitType.ORDER
                ? properties.getOrderPerSecond() : properties.getQuotePerSecond();
        // computeIfAbsent 原子初始化：setRate 完成前同 key 并发线程阻塞，避免 tryAcquire 命中未初始化限流器
        initializedKeys.computeIfAbsent(key, k -> {
            rateLimiterClient.setRate(k, rate);
            return Boolean.TRUE;
        });
        if (!rateLimiterClient.tryAcquire(key)) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
        return pjp.proceed();
    }
}