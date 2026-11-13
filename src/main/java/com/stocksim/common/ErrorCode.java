package com.stocksim.common;

public enum ErrorCode {

    SUCCESS(0, "成功"),

    PARAM_ERROR(1001, "参数错误"),
    UNAUTHORIZED(1002, "未登录"),
    FORBIDDEN(1003, "无权限"),
    RATE_LIMITED(1004, "请求过频"),

    BALANCE_NOT_ENOUGH(2001, "余额不足"),
    ACCOUNT_FROZEN(2002, "账户冻结"),
    POSITION_NOT_ENOUGH(2003, "可卖持仓不足"),
    ACCOUNT_DUPLICATE_REQUEST(2004, "重复提交"),

    PRICE_LIMIT(3001, "涨跌停限制"),
    NOT_LOT_SIZE(3002, "非整手"),
    T_PLUS_ONE(3003, "T+1限制"),
    STOCK_SUSPENDED(3004, "停牌"),
    NOT_TRADING_SESSION(3005, "非交易时段"),
    ORDER_DUPLICATE_REQUEST(3006, "重复提交"),
    CANCEL_NOT_ALLOWED(3007, "状态不允许撤单"),

    NO_COUNTERPARTY(4001, "无对手盘"),
    MATCH_CONFLICT(4002, "撮合冲突重试"),

    SYSTEM_BUSY(5001, "系统繁忙"),
    DOWNSTREAM_TIMEOUT(5002, "下游超时");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}