package com.sunny.kg.circuitbreaker;

import com.sunny.kg.exception.KnowledgeException;

/**
 * 熔断器打开异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class CircuitBreakerOpenException extends KnowledgeException {

    public CircuitBreakerOpenException() {
        super("CIRCUIT_BREAKER_OPEN", "熔断器已打开，请求被拒绝");
    }

}
