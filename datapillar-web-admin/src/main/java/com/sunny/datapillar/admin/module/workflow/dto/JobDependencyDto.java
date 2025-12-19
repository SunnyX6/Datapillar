package com.sunny.datapillar.admin.module.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务依赖 DTO
 *
 * @author sunny
 */
public class JobDependencyDto {

    @Data
    public static class Create {
        @NotNull(message = "任务 ID 不能为空")
        private Long jobId;

        @NotNull(message = "上游任务 ID 不能为空")
        private Long parentJobId;
    }

    @Data
    public static class Response {
        private Long id;
        private Long workflowId;
        private Long jobId;
        private Long parentJobId;
    }
}
