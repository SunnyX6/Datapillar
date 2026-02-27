package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "WorkflowSetJobStateRequest")
public class WorkflowSetJobStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String newState;

    private boolean includeUpstream = false;

    private boolean includeDownstream = false;
}
