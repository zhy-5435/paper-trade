package com.stocksim.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存/布隆封装：Redisson 收敛于此，业务侧无 Redisson API（02§八）。
 * 读路径降级安全——Redis 故障时 get 返回 null（回源 DB）、布隆按「不确定」放行（Q1/R5）；
 * 写/删/布隆加入失败仅告警忽略，不影响业务。MySQL 始终为权威。
 */
@Component
public class RedisCacheClient {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheClient.class);

    private final RedissonClient redisson;
    private final ObjectMapper cacheMapper = createCacheMapper();
    private final ConcurrentMap<Class<?>, TypedJsonCodec> codecs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService delayedEvictScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-delayed-evict");
                t.setDaemon(true);
                return t;
            });

    public RedisCacheClient(RedissonClient redisson) {
        this.redisson = redisson;
    }

    private static ObjectMapper createCacheMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new ParameterNamesModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private TypedJsonCodec codecFor(Class<?> type) {
        return codecs.computeIfAbsent(type, t -> new TypedJsonCodec(t, cacheMapper));
    }

    public <T> T get(String key, Class<T> type) {
        try {
            RBucket<T> bucket = redisson.getBucket(key, codecFor(type));
            return bucket.get();
        } catch (RuntimeException e) {
            log.warn("缓存读失败，回源 DB, key={}", key, e);
            return null;
        }
    }

    public void set(String key, Object value, long ttlMs) {
        try {
            RBucket<Object> bucket = redisson.getBucket(key, codecFor(value.getClass()));
            bucket.set(value, ttlMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.warn("缓存写失败，忽略, key={}", key, e);
        }
    }

    public void delete(String key) {
        try {
            redisson.getBucket(key).delete();
        } catch (RuntimeException e) {
            log.warn("缓存删失败，忽略, key={}", key, e);
        }
    }

    public void deleteByPattern(String pattern) {
        try {
            redisson.getKeys().deleteByPattern(pattern);
        } catch (RuntimeException e) {
            log.warn("缓存批量删失败，忽略, pattern={}", pattern, e);
        }
    }

    /** 管理端变更失效：立即删 + 延迟二次删，防读写并发回填旧值（Q1，停牌状态陈旧会让撮合交易停牌股） */
    public void evictDelayedDouble(String key, long delayMs) {
        delete(key);
        try {
            delayedEvictScheduler.schedule(() -> delete(key), delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.warn("延迟二次删调度失败, key={}", key, e);
        }
    }

    public void initBloom(String key, long expectedInsertions, double falseProbability) {
        try {
            RBloomFilter<String> filter = redisson.getBloomFilter(key, StringCodec.INSTANCE);
            filter.tryInit(expectedInsertions, falseProbability);
        } catch (RuntimeException e) {
            log.warn("布隆初始化失败，读路径将回源 DB, key={}", key, e);
        }
    }

    /** 明确判无才返回 true；布隆未初始化或 Redis 异常一律按「不确定」返回 false，由调用方回源 DB（Q1） */
    public boolean bloomDefinitelyAbsent(String key, String element) {
        try {
            RBloomFilter<String> filter = redisson.getBloomFilter(key, StringCodec.INSTANCE);
            if (!filter.isExists()) {
                return false;
            }
            return !filter.contains(element);
        } catch (RuntimeException e) {
            log.warn("布隆查询失败，按不确定回源 DB, key={}", key, e);
            return false;
        }
    }

    public void bloomAdd(String key, String element) {
        try {
            RBloomFilter<String> filter = redisson.getBloomFilter(key, StringCodec.INSTANCE);
            filter.add(element);
        } catch (RuntimeException e) {
            log.warn("布隆加入失败，忽略, key={}, element={}", key, element, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        delayedEvictScheduler.shutdownNow();
    }
}