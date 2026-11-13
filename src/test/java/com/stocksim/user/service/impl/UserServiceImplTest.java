package com.stocksim.user.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceImplTest {

    @Test
    void masksMobilePhoneMiddleDigits() {
        assertThat(UserServiceImpl.maskPhone("13812341234")).isEqualTo("138****1234");
    }

    @Test
    void nullAndShortPhoneUntouched() {
        assertThat(UserServiceImpl.maskPhone(null)).isNull();
        assertThat(UserServiceImpl.maskPhone("123")).isEqualTo("123");
    }
}