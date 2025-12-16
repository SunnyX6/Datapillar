package com.sunny.job.core.enums;

/**
 * 任务级操作类型枚举
 * <p>
 * 与 job_run.op 数据库字段对应
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public enum JobRunOp {

    /**
     * 手动执行请求（Server -> Worker）
     */
    TRIGGER,

    /**
     * 重试请求（Server -> Worker）
     */
    RETRY,

    /**
     * 终止请求（Server -> Worker）
     */
    KILL,

    /**
     * 跳过请求（Server -> Worker）
     */
    PASS,

    /**
     * 标记失败请求（Server -> Worker）
     */
    MARK_FAILED
}
