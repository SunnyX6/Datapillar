package com.sunny.kg.retry;

import com.sunny.kg.exception.KnowledgeErrorCode;
import com.sunny.kg.exception.KnowledgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 重试执行器
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final RetryPolicy policy;

    public RetryExecutor(RetryPolicy policy) {
        this.policy = policy;
    }

    /**
     * 执行带重试的操作
     */
    public <T> T execute(Supplier<T> action, String operationName) {
        Exception lastException = null;
        int maxAttempts = policy.getMaxRetries() + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;

                if (attempt >= maxAttempts) {
                    log.error("[{}] 执行失败，已达最大重试次数 {}", operationName, policy.getMaxRetries());
                    break;
                }

                if (!isRetryable(e)) {
                    log.error("[{}] 执行失败，异常不可重试: {}", operationName, e.getMessage());
                    break;
                }

                long delayMs = policy.getDelayForAttempt(attempt).toMillis();
                log.warn("[{}] 第 {} 次执行失败，{}ms 后重试: {}",
                    operationName, attempt, delayMs, e.getMessage());

                sleep(delayMs);
            }
        }

        throw new KnowledgeException(
            KnowledgeErrorCode.RETRY_EXHAUSTED,
            lastException,
            operationName
        );
    }

    /**
     * 执行带重试的操作（无返回值）
     */
    public void execute(Runnable action, String operationName) {
        execute(() -> {
            action.run();
            return null;
        }, operationName);
    }

    /**
     * 判断异常是否可重试
     */
    private boolean isRetryable(Exception e) {
        // 连接异常、超时异常可重试
        String message = e.getMessage();
        if (message == null) {
            return true;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("connection")
            || lowerMessage.contains("timeout")
            || lowerMessage.contains("unavailable")
            || lowerMessage.contains("transient");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
