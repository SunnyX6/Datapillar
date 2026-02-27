package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "WorkflowRerunJobRequest")
public class WorkflowRerunJobRequest {

    private boolean downstream = false;

    private boolean upstream = false;
}
