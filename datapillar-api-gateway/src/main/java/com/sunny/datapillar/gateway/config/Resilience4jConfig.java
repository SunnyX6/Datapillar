package com.sunny.datapillar.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4jConfiguration responsibleResilience4jConfigure assembly withBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class Resilience4jConfig {

  /** Default fuse configuration */
  @Bean
  public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
    return factory ->
        factory.configureDefault(
            id ->
                new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(
                        CircuitBreakerConfig.custom()
                            // Failure rate threshold 50%
                            .failureRateThreshold(50)
                            // Slow call rate threshold 50%
                            .slowCallRateThreshold(50)
                            // Slow call time threshold 3 seconds
                            .slowCallDurationThreshold(Duration.ofSeconds(3))
                            // Number of calls allowed in half-open state
                            .permittedNumberOfCallsInHalfOpenState(5)
                            // sliding window size
                            .slidingWindowSize(10)
                            // Minimum number of calls
                            .minimumNumberOfCalls(5)
                            // Waiting time after fuse opens
                            .waitDurationInOpenState(Duration.ofSeconds(30))
                            .build())
                    .timeLimiterConfig(
                        TimeLimiterConfig.custom()
                            // timeout 10 seconds
                            .timeoutDuration(Duration.ofSeconds(10))
                            .build())
                    .build());
  }
}
