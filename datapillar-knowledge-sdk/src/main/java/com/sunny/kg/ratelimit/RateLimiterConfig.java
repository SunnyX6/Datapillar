package com.sunny.kg.ratelimit;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * 限流器配置
 *
 * @author Sunny
 * @since 2025-12-11
 */
@Getter
@Builder
public class RateLimiterConfig {

    /**
     * 每个周期允许的请求数
     */
    @Builder.Default
    private int limitForPeriod = 1000;

    /**
     * 刷新周期
     */
    @Builder.Default
    private Duration limitRefreshPeriod = Duration.ofSeconds(1);

    /**
     * 等待许可的超时时间
     */
    @Builder.Default
    private Duration timeoutDuration = Duration.ofMillis(100);

    public static RateLimiterConfig defaultConfig() {
        return RateLimiterConfig.builder().build();
    }

}
