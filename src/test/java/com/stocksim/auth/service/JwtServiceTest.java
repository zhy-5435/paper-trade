package com.stocksim.auth.service;

import com.stocksim.auth.properties.JwtProperties;
import com.stocksim.common.context.UserContext.LoginUser;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService serviceWithExpireHours(long hours) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-unit-test-secret-123456");
        properties.setExpireHours(hours);
        return new JwtService(properties);
    }

    @Test
    void generateAndParseRoundTrip() {
        JwtService service = serviceWithExpireHours(24);
        String token = service.generate(42L, "user01", 1);
        LoginUser user = service.parse(token);
        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.username()).isEqualTo("user01");
        assertThat(user.role()).isEqualTo(1);
    }

    @Test
    void expiredTokenRejected() {
        JwtService service = serviceWithExpireHours(-1);
        String token = service.generate(42L, "user01", 1);
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenRejected() {
        JwtService service = serviceWithExpireHours(24);
        String token = service.generate(42L, "user01", 1);
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageTokenRejected() {
        JwtService service = serviceWithExpireHours(24);
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretRejected() {
        String token = serviceWithExpireHours(24).generate(42L, "user01", 1);
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("another-secret-another-secret-123456789");
        otherProps.setExpireHours(24);
        JwtService otherService = new JwtService(otherProps);
        assertThatThrownBy(() -> otherService.parse(token)).isInstanceOf(JwtException.class);
    }
}