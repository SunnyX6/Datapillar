package com.sunny.job.core.enums;

/**
 * 任务状态
 * <p>
 * 用于 job_run 和 job_workflow_run 的状态流转
 * <p>
 * 状态流转：
 * <pre>
 * WAITING ──调度器分发──→ RUNNING ──执行成功──→ SUCCESS
 *                           │
 *                           ├──执行失败──→ FAIL ──可重试──→ WAITING
 *                           │               └──不可重试──→ FAIL（终态）
 *                           │
 *                           └──执行超时──→ TIMEOUT（终态）
 * </pre>
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum JobStatus {

    WAITING(0, "等待中"),
    RUNNING(1, "运行中"),
    SUCCESS(2, "成功"),
    FAIL(3, "失败"),
    CANCEL(4, "取消"),
    TIMEOUT(5, "超时"),
    SKIPPED(6, "跳过");

    private final int code;
    private final String desc;

    JobStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAIL || this == CANCEL || this == TIMEOUT || this == SKIPPED;
    }

    /**
     * 是否为成功状态
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 是否可重试（仅 FAIL 状态可重试，TIMEOUT 不重试）
     */
    public boolean canRetry() {
        return this == FAIL;
    }

    public static JobStatus of(int code) {
        for (JobStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的任务状态: " + code);
    }
}
