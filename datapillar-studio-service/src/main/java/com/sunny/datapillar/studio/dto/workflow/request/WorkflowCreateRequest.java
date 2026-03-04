package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "WorkflowCreate")
public class WorkflowCreateRequest {

  @NotNull(message = "ProjectIDcannot be empty")
  private Long projectId;

  @NotBlank(message = "Workflow name cannot be empty")
  private String workflowName;

  private Integer triggerType;

  private String triggerValue;

  private Integer timeoutSeconds;

  private Integer maxRetryTimes;

  private Integer priority;

  private String description;
}
