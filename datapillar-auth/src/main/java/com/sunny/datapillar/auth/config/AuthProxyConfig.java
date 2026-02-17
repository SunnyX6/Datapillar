package com.sunny.datapillar.auth.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 认证代理配置
 * 负责认证代理客户端配置与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class AuthProxyConfig {

    @Bean
    public RestTemplate authProxyRestTemplate(RestTemplateBuilder builder, AuthProxyProperties proxyProperties) {
        return builder
                .connectTimeout(Duration.ofMillis(Math.max(1, proxyProperties.getConnectTimeoutMs())))
                .readTimeout(Duration.ofMillis(Math.max(1, proxyProperties.getReadTimeoutMs())))
                .build();
    }
}
