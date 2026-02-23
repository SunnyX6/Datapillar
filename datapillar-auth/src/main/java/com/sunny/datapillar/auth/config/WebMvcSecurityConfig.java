package com.sunny.datapillar.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sunny.datapillar.auth.security.AuthCsrfInterceptor;
/**
 * WebMvc安全配置
 * 负责WebMvc安全配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final AuthCsrfInterceptor authCsrfInterceptor;

    public WebMvcSecurityConfig(AuthCsrfInterceptor authCsrfInterceptor) {
        this.authCsrfInterceptor = authCsrfInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authCsrfInterceptor)
                .addPathPatterns("/auth/**", "/login/**");
    }
}
