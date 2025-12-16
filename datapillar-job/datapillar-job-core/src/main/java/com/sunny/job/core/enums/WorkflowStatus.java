package com.sunny.job.core.enums;

/**
 * 工作流状态
 * <p>
 * 用于 job_workflow 的上线/下线状态
 * <p>
 * 状态流转：
 * <pre>
 * DRAFT ──上线──→ ONLINE ──下线──→ OFFLINE
 *                   ↑                │
 *                   └──── 重新上线 ───┘
 * </pre>
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public enum WorkflowStatus {

    DRAFT(0, "草稿"),
    ONLINE(1, "已上线"),
    OFFLINE(2, "已下线");

    private final int code;
    private final String desc;

    WorkflowStatus(int code, String desc) {
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
     * 是否可以上线
     */
    public boolean canOnline() {
        return this == DRAFT || this == OFFLINE;
    }

    /**
     * 是否可以下线
     */
    public boolean canOffline() {
        return this == ONLINE;
    }

    /**
     * 是否已上线
     */
    public boolean isOnline() {
        return this == ONLINE;
    }

    public static WorkflowStatus of(int code) {
        for (WorkflowStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的工作流状态: " + code);
    }

    public static WorkflowStatus of(Integer code) {
        if (code == null) {
            return DRAFT;
        }
        return of(code.intValue());
    }
}
