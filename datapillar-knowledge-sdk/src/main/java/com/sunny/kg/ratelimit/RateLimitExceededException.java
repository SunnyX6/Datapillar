package com.sunny.kg.ratelimit;

import com.sunny.kg.exception.KnowledgeException;

/**
 * 限流异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class RateLimitExceededException extends KnowledgeException {

    public RateLimitExceededException() {
        super("RATE_LIMIT_EXCEEDED", "请求频率超过限制");
    }

}
