package com.sunny.datapillar.gateway.config;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.security.ClientIpResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 限流限流器配置
 * 负责限流限流器配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class RateLimiterConfig {

    private final ClientIpResolver clientIpResolver;

    public RateLimiterConfig(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 基于 IP 的限流 Key 解析器
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIpResolver.resolve(exchange.getRequest()));
    }

    /**
     * 基于用户 ID 的限流 Key 解析器
     * 用于认证后的请求
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_USER_ID);
            if (userId != null && !userId.isEmpty()) {
                return Mono.just("user:" + userId);
            }
            // 未认证用户使用 IP
            return ipKeyResolver().resolve(exchange).map(ip -> "ip:" + ip);
        };
    }
}
