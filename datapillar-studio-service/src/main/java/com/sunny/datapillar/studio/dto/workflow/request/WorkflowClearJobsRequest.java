package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "WorkflowClearJobsRequest")
public class WorkflowClearJobsRequest {

  @NotNull(message = "TaskIDList cannot be empty")
  private List<String> jobIds;

  private boolean onlyFailed = true;

  private boolean resetDagRuns = true;

  private boolean includeUpstream = false;

  private boolean includeDownstream = false;
}
