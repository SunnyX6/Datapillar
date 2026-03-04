package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "JobDependencyCreate")
public class JobDependencyCreateRequest {

  @NotNull(message = "Task ID cannot be empty")
  private Long jobId;

  @NotNull(message = "upstream tasks ID cannot be empty")
  private Long parentJobId;
}
