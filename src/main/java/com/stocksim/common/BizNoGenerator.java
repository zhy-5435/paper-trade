package com.stocksim.common;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

@Component
public class BizNoGenerator {

    private final IdentifierGenerator identifierGenerator;

    public BizNoGenerator(IdentifierGenerator identifierGenerator) {
        this.identifierGenerator = identifierGenerator;
    }

    public String orderNo() {
        return "O" + identifierGenerator.nextId(null);
    }

    public String dealNo() {
        return "D" + identifierGenerator.nextId(null);
    }

    public String accountNo() {
        return "A" + identifierGenerator.nextId(null);
    }

    public String flowNo() {
        return "F" + identifierGenerator.nextId(null);
    }

    public static String orderFreezeFund(String orderNo) {
        return orderNo + "F3";
    }

    public static String orderFreezePosition(String orderNo) {
        return orderNo + "P4";
    }

    public static String buyDealFund(String dealNo) {
        return dealNo + "B5";
    }

    public static String buyFeeFund(String dealNo) {
        return dealNo + "B7";
    }

    public static String buyPosition(String dealNo) {
        return dealNo + "B1";
    }

    public static String sellDealFund(String dealNo) {
        return dealNo + "S6";
    }

    public static String sellFeeFund(String dealNo) {
        return dealNo + "S7";
    }

    public static String sellPosition(String dealNo) {
        return dealNo + "S2";
    }

    public static String cancelFund(String orderNo) {
        return orderNo + "C4";
    }

    public static String cancelPosition(String orderNo) {
        return orderNo + "C5";
    }
}