package com.sunny.job.core.enums;

/**
 * 工作流操作类型枚举
 * <p>
 * 针对工作流（workflow）级别的操作
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public enum WorkflowOp {

    /**
     * 上线
     */
    ONLINE,

    /**
     * 下线
     */
    OFFLINE,

    /**
     * 手动触发
     */
    MANUAL_TRIGGER
}
