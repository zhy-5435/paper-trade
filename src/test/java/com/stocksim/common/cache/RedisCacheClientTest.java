package com.stocksim.common.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheClientTest {

    @Mock
    private RedissonClient redisson;

    @Test
    @SuppressWarnings("unchecked")
    void get返回缓存值() {
        RedisCacheClient client = new RedisCacheClient(redisson);
        RBucket<Object> bucket = mock(RBucket.class);
        when(redisson.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.get()).thenReturn("cached");
        assertThat(client.get("k", String.class)).isEqualTo("cached");
    }

    @Test
    @SuppressWarnings("unchecked")
    void get故障降级返回null回源() {
        RedisCacheClient client = new RedisCacheClient(redisson);
        RBucket<Object> bucket = mock(RBucket.class);
        when(redisson.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.get()).thenThrow(new RedisException("redis down"));
        assertThat(client.get("k", String.class)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 布隆明确判无返回true() {
        RedisCacheClient client = new RedisCacheClient(redisson);
        RBloomFilter<String> filter = mock(RBloomFilter.class);
        doReturn(filter).when(redisson).getBloomFilter(anyString(), any());
        when(filter.isExists()).thenReturn(true);
        when(filter.contains("600000")).thenReturn(false);
        assertThat(client.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, "600000")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 布隆可能存在返回false() {
        RedisCacheClient client = new RedisCacheClient(redisson);
        RBloomFilter<String> filter = mock(RBloomFilter.class);
        doReturn(filter).when(redisson).getBloomFilter(anyString(), any());
        when(filter.isExists()).thenReturn(true);
        when(filter.contains("600000")).thenReturn(true);
        assertThat(client.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, "600000")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 布隆未初始化按不确定返回false() {
        RedisCacheClient client = new RedisCacheClient(redisson);
        RBloomFilter<String> filter = mock(RBloomFilter.class);
        doReturn(filter).when(redisson).getBloomFilter(anyString(), any());
        when(filter.isExists()).thenReturn(false);
        assertThat(client.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, "600000")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 布隆故障按不确定返回false() {
        RedisCacheClient client = new RedisCacheClient(redisson);
        RBloomFilter<String> filter = mock(RBloomFilter.class);
        doReturn(filter).when(redisson).getBloomFilter(anyString(), any());
        when(filter.isExists()).thenThrow(new RedisException("redis down"));
        assertThat(client.bloomDefinitelyAbsent(CacheKeys.BLOOM_STOCK, "600000")).isFalse();
    }
}