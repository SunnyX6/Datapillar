package com.sunny.datapillar.gateway.config;

import com.sunny.datapillar.common.constant.HeaderConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 限流配置
 * 基于 Redis 的请求限流
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 基于 IP 的限流 Key 解析器
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
            }
            // 多个代理时取第一个
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return Mono.just(ip);
        };
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
