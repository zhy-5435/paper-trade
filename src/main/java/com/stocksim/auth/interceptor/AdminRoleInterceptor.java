package com.stocksim.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.Result;
import com.stocksim.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AdminRoleInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Integer role = UserContext.role();
        if (role == null) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return false;
        }
        if (role != 2) {
            reject(response, ErrorCode.FORBIDDEN);
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(errorCode)));
    }
}