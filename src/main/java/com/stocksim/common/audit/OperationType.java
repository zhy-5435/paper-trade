package com.stocksim.common.audit;

/** 操作审计类型（03§五.17）：1登录 2股票维护 3行情管理 4差错处理 5其他。 */
public enum OperationType {

    LOGIN(1),
    STOCK_MAINTAIN(2),
    QUOTE_MANAGE(3),
    ERROR_HANDLE(4),
    OTHER(5);

    private final int code;

    OperationType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}