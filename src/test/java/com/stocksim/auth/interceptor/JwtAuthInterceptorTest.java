package com.stocksim.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksim.auth.properties.JwtProperties;
import com.stocksim.auth.service.JwtService;
import com.stocksim.common.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthInterceptorTest {

    private final JwtService jwtService = newJwtService();
    private final JwtAuthInterceptor interceptor = new JwtAuthInterceptor(jwtService, new ObjectMapper());
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static JwtService newJwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-unit-test-secret-123456");
        properties.setExpireHours(24);
        return new JwtService(properties);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void missingTokenRejectedWith1002() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("1002");
    }

    @Test
    void nonBearerHeaderRejected() throws Exception {
        request.addHeader("Authorization", "Basic abc");
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("1002");
    }

    @Test
    void validTokenSetsUserContext() throws Exception {
        String token = jwtService.generate(42L, "user01", 1);
        request.addHeader("Authorization", "Bearer " + token);
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(UserContext.userId()).isEqualTo(42L);
        assertThat(UserContext.role()).isEqualTo(1);
    }

    @Test
    void invalidTokenRejectedWith1002() throws Exception {
        request.addHeader("Authorization", "Bearer garbage");
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("1002");
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void afterCompletionClearsContext() throws Exception {
        UserContext.set(new UserContext.LoginUser(42L, "user01", 1));
        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(UserContext.get()).isNull();
    }
}