package com.sunny.kg.circuitbreaker;

import com.sunny.kg.exception.KnowledgeErrorCode;
import com.sunny.kg.exception.KnowledgeException;

/**
 * 熔断器打开异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class CircuitBreakerOpenException extends KnowledgeException {

    public CircuitBreakerOpenException() {
        super(KnowledgeErrorCode.CIRCUIT_BREAKER_OPEN);
    }

}
