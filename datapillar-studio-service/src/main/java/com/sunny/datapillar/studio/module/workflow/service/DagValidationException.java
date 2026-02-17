package com.sunny.datapillar.studio.module.workflow.service;

/**
 * DAGValidation异常
 * 描述DAGValidation异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class DagValidationException extends com.sunny.datapillar.studio.module.workflow.service.dag.DagValidationException {

    public DagValidationException(String message) {
        super(message);
    }

    public DagValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
