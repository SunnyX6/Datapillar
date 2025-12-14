package com.sunny.job.core.dag;

/**
 * DAG 验证异常
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class DagValidationException extends RuntimeException {

    public DagValidationException(String message) {
        super(message);
    }

    public DagValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
