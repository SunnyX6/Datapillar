package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "JobCreate")
public class JobCreateRequest {

  @NotBlank(message = "Task name cannot be empty")
  private String jobName;

  @NotNull(message = "Task type cannot be empty")
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
