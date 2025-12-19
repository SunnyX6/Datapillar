package com.sunny.datapillar.admin.module.workflow.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务 DTO
 *
 * @author sunny
 */
public class JobDto {

    @Data
    public static class Create {
        @NotBlank(message = "任务名称不能为空")
        private String jobName;

        @NotNull(message = "任务类型不能为空")
        private Long jobType;

        private Map<String, Object> jobParams;

        private Integer timeoutSeconds = 0;

        private Integer maxRetryTimes = 0;

        private Integer retryInterval = 0;

        private Integer priority = 0;

        private Double positionX;

        private Double positionY;

        private String description;
    }

    @Data
    public static class Update {
        private String jobName;
        private Long jobType;
        private Map<String, Object> jobParams;
        private Integer timeoutSeconds;
        private Integer maxRetryTimes;
        private Integer retryInterval;
        private Integer priority;
        private Double positionX;
        private Double positionY;
        private String description;
    }

    @Data
    public static class Response {
        private Long id;
        private Long workflowId;
        private String jobName;
        private Long jobType;
        /** 组件代码 (SHELL, PYTHON, SQL 等) */
        private String jobTypeCode;
        /** 组件名称 */
        private String jobTypeName;
        private Map<String, Object> jobParams;
        private Integer timeoutSeconds;
        private Integer maxRetryTimes;
        private Integer retryInterval;
        private Integer priority;
        private Double positionX;
        private Double positionY;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== 布局 ====================

    @Data
    public static class LayoutSave {
        @NotEmpty(message = "位置列表不能为空")
        @Valid
        private List<Position> positions;
    }

    @Data
    public static class Position {
        @NotNull(message = "任务 ID 不能为空")
        private Long jobId;

        private Double positionX;

        private Double positionY;
    }
}
