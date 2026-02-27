package com.sunny.datapillar.studio.dto.workflow.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "WorkflowRunResponse")
public class WorkflowRunResponse {

    private String runId;

    private String state;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
