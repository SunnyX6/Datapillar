package com.sunny.job.admin.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置类
 * 注册 Gateway 用户过滤器
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Configuration
public class SecurityConfig {

    private final GatewayUserFilter gatewayUserFilter;

    public SecurityConfig(GatewayUserFilter gatewayUserFilter) {
        this.gatewayUserFilter = gatewayUserFilter;
    }

    @Bean
    public FilterRegistrationBean<GatewayUserFilter> gatewayUserFilterRegistration() {
        FilterRegistrationBean<GatewayUserFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(gatewayUserFilter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
