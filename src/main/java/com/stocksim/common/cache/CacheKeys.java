package com.stocksim.common.cache;

/** 缓存 key 约定（R2规划§6.5）：行情短 TTL、股票信息长 TTL、股票布隆。 */
public final class CacheKeys {

    public static final String BLOOM_STOCK = "bloom:stock";
    public static final String QUOTE_PATTERN = "quote:*";

    private CacheKeys() {
    }

    public static String quote(String stockCode) {
        return "quote:" + stockCode;
    }

    public static String stock(String stockCode) {
        return "stock:" + stockCode;
    }
}