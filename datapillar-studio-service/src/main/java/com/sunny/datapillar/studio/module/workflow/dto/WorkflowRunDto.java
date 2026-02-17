package com.sunny.datapillar.studio.module.workflow.dto;

import java.time.LocalDateTime;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作流Run数据传输对象
 * 定义工作流Run数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class WorkflowRunDto {

    @Data
    @Schema(name = "WorkflowRunResponse")
    public static class Response {
        private String runId;
        private String state;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
}
