package com.sunny.kg.ratelimit;

import com.sunny.kg.exception.KnowledgeErrorCode;
import com.sunny.kg.exception.KnowledgeException;

/**
 * 限流异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class RateLimitExceededException extends KnowledgeException {

    public RateLimitExceededException() {
        super(KnowledgeErrorCode.RATE_LIMIT_EXCEEDED);
    }

}
