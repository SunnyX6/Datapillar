package com.sunny.datapillar.studio.dto.workflow.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "WorkflowResponse")
public class WorkflowResponse {

    private Long id;

    private Long projectId;

    private String projectName;

    private String workflowName;

    private Integer triggerType;

    private String triggerValue;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer priority;

    private Integer status;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<JobResponse> jobs;

    private List<JobDependencyResponse> dependencies;
}
