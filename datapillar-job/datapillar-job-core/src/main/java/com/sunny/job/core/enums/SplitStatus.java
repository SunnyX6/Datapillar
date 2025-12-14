package com.sunny.job.core.enums;

/**
 * 分片状态
 * <p>
 * 用于 SHARDING 策略下的 Split 范围状态管理
 * <p>
 * 状态流转：
 * <pre>
 * PENDING ──Worker标记──→ PROCESSING ──执行成功──→ COMPLETED
 *                            │
 *                            └──执行失败/Worker挂了──→ PENDING（重新分配）
 * </pre>
 *
 * @author Sunny
 */
public enum SplitStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    private final int code;
    private final String desc;

    SplitStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isSuccess() {
        return this == COMPLETED;
    }

    public static SplitStatus of(int code) {
        for (SplitStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的分片状态: " + code);
    }
}
