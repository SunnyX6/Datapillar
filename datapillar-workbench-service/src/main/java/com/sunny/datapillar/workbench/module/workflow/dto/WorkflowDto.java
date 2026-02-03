package com.sunny.datapillar.workbench.module.workflow.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工作流 DTO
 *
 * @author sunny
 */
public class WorkflowDto {

    @Data
    public static class Create {
        private Long projectId;

        @NotBlank(message = "工作流名称不能为空")
        private String workflowName;

        /** 触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API */
        private Integer triggerType;

        /** 触发配置（CRON表达式或秒数） */
        private String triggerValue;

        private Integer timeoutSeconds;

        private Integer maxRetryTimes;

        private Integer priority;

        private String description;
    }

    @Data
    public static class Update {
        private String workflowName;
        private Integer triggerType;
        private String triggerValue;
        private Integer timeoutSeconds;
        private Integer maxRetryTimes;
        private Integer priority;
        private String description;
    }

    @Data
    public static class Response {
        private Long id;
        private Long projectId;
        private String projectName;
        private String workflowName;
        private Integer triggerType;
        private String triggerValue;
        private Integer timeoutSeconds;
        private Integer maxRetryTimes;
        private Integer priority;
        /** 状态: 0-草稿, 1-已发布, 2-已暂停 */
        private Integer status;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        /** 任务列表 */
        private List<JobDto.Response> jobs;
        /** 依赖关系列表 */
        private List<JobDependencyDto.Response> dependencies;
    }

    @Data
    public static class ListItem {
        private Long id;
        private Long projectId;
        private String projectName;
        private String workflowName;
        private Integer triggerType;
        private Integer status;
        private String description;
        private Integer jobCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== Airflow 交互请求 ====================

    /**
     * 触发DAG运行请求
     */
    @Data
    public static class TriggerRequest {
        /** ISO格式时间，不填则使用当前时间 */
        private String logicalDate;
        /** 运行时配置参数 */
        private Map<String, Object> conf;
    }

    /**
     * 重跑任务请求
     */
    @Data
    public static class RerunJobRequest {
        /** 是否重跑下游任务 */
        private boolean downstream = false;
        /** 是否重跑上游任务 */
        private boolean upstream = false;
    }

    /**
     * 设置任务状态请求
     */
    @Data
    public static class SetJobStateRequest {
        /** 新状态: success, failed, skipped */
        @NotBlank(message = "状态不能为空")
        private String newState;
        /** 是否包含上游任务 */
        private boolean includeUpstream = false;
        /** 是否包含下游任务 */
        private boolean includeDownstream = false;
    }

    /**
     * 批量清除任务请求
     */
    @Data
    public static class ClearJobsRequest {
        /** 任务ID列表 */
        @NotNull(message = "任务ID列表不能为空")
        private List<String> jobIds;
        /** 只清除失败的任务 */
        private boolean onlyFailed = true;
        /** 是否重置DAG Run状态 */
        private boolean resetDagRuns = true;
        /** 是否包含上游任务 */
        private boolean includeUpstream = false;
        /** 是否包含下游任务 */
        private boolean includeDownstream = false;
    }
}
