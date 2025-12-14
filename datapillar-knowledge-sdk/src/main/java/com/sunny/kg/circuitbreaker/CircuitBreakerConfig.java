package com.sunny.kg.circuitbreaker;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * 熔断器配置
 *
 * @author Sunny
 * @since 2025-12-11
 */
@Getter
@Builder
public class CircuitBreakerConfig {

    /**
     * 失败率阈值（0-100），超过则熔断
     */
    @Builder.Default
    private int failureRateThreshold = 50;

    /**
     * 滑动窗口大小（请求数）
     */
    @Builder.Default
    private int slidingWindowSize = 10;

    /**
     * 熔断后等待时间
     */
    @Builder.Default
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);

    /**
     * 半开状态允许的请求数
     */
    @Builder.Default
    private int permittedCallsInHalfOpenState = 3;

    /**
     * 最小请求数（低于此数不计算失败率）
     */
    @Builder.Default
    private int minimumNumberOfCalls = 5;

    public static CircuitBreakerConfig defaultConfig() {
        return CircuitBreakerConfig.builder().build();
    }

}
