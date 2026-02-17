package com.sunny.datapillar.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j配置
 * 负责Resilience4j配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class Resilience4jConfig {

    /**
     * 默认熔断器配置
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        // 失败率阈值 50%
                        .failureRateThreshold(50)
                        // 慢调用率阈值 50%
                        .slowCallRateThreshold(50)
                        // 慢调用时间阈值 3 秒
                        .slowCallDurationThreshold(Duration.ofSeconds(3))
                        // 半开状态允许的调用次数
                        .permittedNumberOfCallsInHalfOpenState(5)
                        // 滑动窗口大小
                        .slidingWindowSize(10)
                        // 最小调用次数
                        .minimumNumberOfCalls(5)
                        // 熔断器开启后等待时间
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        // 超时时间 10 秒
                        .timeoutDuration(Duration.ofSeconds(10))
                        .build())
                .build());
    }
}
