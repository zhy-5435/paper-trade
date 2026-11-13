package com.stocksim.match.engine;

import java.math.BigDecimal;

/** 一笔成交的上下文（一笔成交一次通知，第二轮拆买/卖两条消息不改上层） */
public record DealContext(String dealNo, long dealSeq, String stockCode, BigDecimal dealPrice, long dealQty,
                          String buyerOrderNo, String sellerOrderNo, Long buyerUserId, Long sellerUserId) {
}