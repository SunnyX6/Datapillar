package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "WorkflowCreate")
public class WorkflowCreateRequest {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "工作流名称不能为空")
    private String workflowName;

    private Integer triggerType;

    private String triggerValue;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer priority;

    private String description;
}
