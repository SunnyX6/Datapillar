package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "WorkflowTriggerRequest")
public class WorkflowTriggerRequest {

    private String logicalDate;

    private Map<String, Object> conf;
}
