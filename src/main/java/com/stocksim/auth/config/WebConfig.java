package com.stocksim.auth.config;

import com.stocksim.auth.interceptor.AdminRoleInterceptor;
import com.stocksim.auth.interceptor.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AdminRoleInterceptor adminRoleInterceptor;

    public WebConfig(JwtAuthInterceptor jwtAuthInterceptor, AdminRoleInterceptor adminRoleInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.adminRoleInterceptor = adminRoleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/register", "/api/auth/login");
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}