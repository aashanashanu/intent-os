package com.intentos.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiAuthenticationConfiguration implements WebMvcConfigurer {
    private final ApiAuthenticationInterceptor interceptor;

    public ApiAuthenticationConfiguration(ApiAuthenticationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(
                "/api/intents/**", "/api/shopping-history/**", "/api/auth/logout");
    }
}
