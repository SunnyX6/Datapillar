package com.sunny.kg.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滑动窗口限流器实现
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private final RateLimiterConfig config;
    private final AtomicInteger permits;
    private final AtomicLong lastRefreshTime;

    public SlidingWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.permits = new AtomicInteger(config.getLimitForPeriod());
        this.lastRefreshTime = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int requestedPermits) {
        refreshIfNeeded();

        int current;
        do {
            current = permits.get();
            if (current < requestedPermits) {
                return false;
            }
        } while (!permits.compareAndSet(current, current - requestedPermits));

        return true;
    }

    @Override
    public void acquire() {
        long deadline = System.currentTimeMillis() + config.getTimeoutDuration().toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException();
            }
        }

        throw new RateLimitExceededException();
    }

    @Override
    public int availablePermits() {
        refreshIfNeeded();
        return permits.get();
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        long lastRefresh = lastRefreshTime.get();
        long periodMs = config.getLimitRefreshPeriod().toMillis();

        if (now - lastRefresh >= periodMs) {
            if (lastRefreshTime.compareAndSet(lastRefresh, now)) {
                permits.set(config.getLimitForPeriod());
                log.trace("限流器刷新, 可用许可: {}", config.getLimitForPeriod());
            }
        }
    }

}
