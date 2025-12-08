package com.sunny.job.admin.scheduler.trigger;

/**
 * trigger type enum
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public enum TriggerTypeEnum {

    DAG("DAG依赖触发"),
    MANUAL_SINGLE("手动单任务执行"),
    MANUAL_CASCADE("手动级联执行"),
    DEBUG("IDE调试执行");

    private TriggerTypeEnum(String title){
        this.title = title;
    }
    private String title;
    public String getTitle() {
        return title;
    }

}
