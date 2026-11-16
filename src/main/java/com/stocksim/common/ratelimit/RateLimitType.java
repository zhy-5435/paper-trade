package com.stocksim.common.ratelimit;

/** 限流场景：key=rl:{type}:{userId}，阈值见 RateLimitProperties（Q8）。 */
public enum RateLimitType {
    ORDER,
    QUOTE
}