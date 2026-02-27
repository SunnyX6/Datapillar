package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "WorkflowUpdate")
public class WorkflowUpdateRequest {

    private String workflowName;

    private Integer triggerType;

    private String triggerValue;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer priority;

    private String description;
}
