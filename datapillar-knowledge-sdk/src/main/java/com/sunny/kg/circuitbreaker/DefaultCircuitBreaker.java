package com.sunny.kg.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认熔断器实现
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class DefaultCircuitBreaker implements CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(DefaultCircuitBreaker.class);

    private final CircuitBreakerConfig config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
    private volatile Instant openedAt;

    public DefaultCircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
    }

    @Override
    public boolean tryAcquire() {
        State currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                if (shouldTransitionToHalfOpen()) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        halfOpenCalls.set(0);
                        log.info("熔断器状态: OPEN -> HALF_OPEN");
                    }
                    return halfOpenCalls.incrementAndGet() <= config.getPermittedCallsInHalfOpenState();
                }
                return false;

            case HALF_OPEN:
                return halfOpenCalls.incrementAndGet() <= config.getPermittedCallsInHalfOpenState();

            default:
                return false;
        }
    }

    @Override
    public void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            successCount.incrementAndGet();
            if (successCount.get() >= config.getPermittedCallsInHalfOpenState()) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    resetCounters();
                    log.info("熔断器状态: HALF_OPEN -> CLOSED");
                }
            }
        } else if (currentState == State.CLOSED) {
            successCount.incrementAndGet();
            checkThreshold();
        }
    }

    @Override
    public void recordFailure() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openedAt = Instant.now();
                log.warn("熔断器状态: HALF_OPEN -> OPEN (半开状态失败)");
            }
        } else if (currentState == State.CLOSED) {
            failureCount.incrementAndGet();
            checkThreshold();
        }
    }

    private void checkThreshold() {
        int total = successCount.get() + failureCount.get();
        if (total < config.getMinimumNumberOfCalls()) {
            return;
        }

        // 滑动窗口
        if (total > config.getSlidingWindowSize()) {
            int excess = total - config.getSlidingWindowSize();
            successCount.addAndGet(-excess / 2);
            failureCount.addAndGet(-excess / 2);
        }

        int failures = failureCount.get();
        int totalCalls = successCount.get() + failures;
        if (totalCalls > 0) {
            int failureRate = (failures * 100) / totalCalls;
            if (failureRate >= config.getFailureRateThreshold()) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openedAt = Instant.now();
                    log.warn("熔断器状态: CLOSED -> OPEN (失败率: {}%)", failureRate);
                }
            }
        }
    }

    private boolean shouldTransitionToHalfOpen() {
        if (openedAt == null) {
            return false;
        }
        return Instant.now().isAfter(openedAt.plus(config.getWaitDurationInOpenState()));
    }

    private void resetCounters() {
        successCount.set(0);
        failureCount.set(0);
        halfOpenCalls.set(0);
    }

    @Override
    public State getState() {
        return state.get();
    }

    @Override
    public void reset() {
        state.set(State.CLOSED);
        resetCounters();
        openedAt = null;
        log.info("熔断器已重置");
    }

}
