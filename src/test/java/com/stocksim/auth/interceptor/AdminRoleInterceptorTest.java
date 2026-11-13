package com.stocksim.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksim.common.context.UserContext;
import com.stocksim.common.context.UserContext.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRoleInterceptorTest {

    private final AdminRoleInterceptor interceptor = new AdminRoleInterceptor(new ObjectMapper());
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void adminAllowed() throws Exception {
        UserContext.set(new LoginUser(1L, "admin", 2));
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void normalUserRejectedWith1003() throws Exception {
        UserContext.set(new LoginUser(2L, "user01", 1));
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("1003");
    }

    @Test
    void missingContextRejectedWith1002() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("1002");
    }
}