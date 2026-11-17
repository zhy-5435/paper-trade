package com.stocksim.stock.cache;

import com.stocksim.common.cache.CacheKeys;
import com.stocksim.common.cache.RedisCacheClient;
import com.stocksim.common.properties.CacheProperties;
import com.stocksim.stock.entity.StockInfo;
import com.stocksim.stock.mapper.StockInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动初始化股票布隆过滤器并装入全部代码（Q1：未初始化 contains 恒 false，必须启动初始化）。
 * Redis 不可用时 initBloom/bloomAdd 降级忽略，读路径按「不确定」回源 DB，不阻塞启动。
 */
@Component
public class StockCacheInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockCacheInitializer.class);

    private final RedisCacheClient cacheClient;
    private final CacheProperties cacheProperties;
    private final StockInfoMapper stockInfoMapper;

    public StockCacheInitializer(RedisCacheClient cacheClient, CacheProperties cacheProperties,
                                 StockInfoMapper stockInfoMapper) {
        this.cacheClient = cacheClient;
        this.cacheProperties = cacheProperties;
        this.stockInfoMapper = stockInfoMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        cacheClient.initBloom(CacheKeys.BLOOM_STOCK,
                cacheProperties.getBloomExpectedInsertions(), cacheProperties.getBloomFalseProbability());
        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        for (StockInfo stock : stocks) {
            cacheClient.bloomAdd(CacheKeys.BLOOM_STOCK, stock.getStockCode());
        }
        log.info("股票布隆过滤器初始化完成, 装入代码数={}", stocks.size());
    }
}