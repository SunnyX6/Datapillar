package com.sunny.datapillar.studio.module.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务Dependency数据传输对象
 * 定义任务Dependency数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class JobDependencyDto {

    @Data
    @Schema(name = "JobDependencyCreate")
    public static class Create {
        @NotNull(message = "任务 ID 不能为空")
        private Long jobId;

        @NotNull(message = "上游任务 ID 不能为空")
        private Long parentJobId;
    }

    @Data
    @Schema(name = "JobDependencyResponse")
    public static class Response {
        private Long id;
        private Long workflowId;
        private Long jobId;
        private Long parentJobId;
    }
}
