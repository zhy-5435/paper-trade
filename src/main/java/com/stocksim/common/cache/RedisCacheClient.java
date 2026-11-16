package com.stocksim.common.cache;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存/布隆封装：Redisson 收敛于此，业务侧无 Redisson API（02§八）。
 * 序列化用 JsonJacksonCodec（默认 codec 不适配 record，Q1）。M1 接入 stock 读路径，本轮空壳不接业务。
 */
@Component
public class RedisCacheClient {

    private final RedissonClient redisson;

    public RedisCacheClient(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public <T> T get(String key) {
        RBucket<T> bucket = redisson.getBucket(key, JsonJacksonCodec.INSTANCE);
        return bucket.get();
    }

    public void set(String key, Object value, long ttlMs) {
        RBucket<Object> bucket = redisson.getBucket(key, JsonJacksonCodec.INSTANCE);
        bucket.set(value, ttlMs, TimeUnit.MILLISECONDS);
    }

    public void delete(String key) {
        redisson.getBucket(key, JsonJacksonCodec.INSTANCE).delete();
    }

    public void initBloom(String key, long expectedInsertions, double falseProbability) {
        RBloomFilter<String> filter = redisson.getBloomFilter(key);
        filter.tryInit(expectedInsertions, falseProbability);
    }

    public boolean bloomMightContain(String key, String element) {
        RBloomFilter<String> filter = redisson.getBloomFilter(key);
        return filter.contains(element);
    }

    public void bloomAdd(String key, String element) {
        RBloomFilter<String> filter = redisson.getBloomFilter(key);
        filter.add(element);
    }
}