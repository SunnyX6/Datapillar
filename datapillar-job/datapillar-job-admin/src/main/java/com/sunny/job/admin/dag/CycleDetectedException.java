package com.sunny.job.admin.dag;

/**
 * 循环依赖检测异常
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
public class CycleDetectedException extends Exception {

    public CycleDetectedException(String message) {
        super(message);
    }

    public CycleDetectedException(String message, Throwable cause) {
        super(message, cause);
    }
}