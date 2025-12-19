package com.sunny.datapillar.admin.module.workflow.dag;

/**
 * DAG 验证异常
 *
 * @author sunny
 */
public class DagValidationException extends RuntimeException {

    public DagValidationException(String message) {
        super(message);
    }

    public DagValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
