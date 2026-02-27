package com.sunny.datapillar.studio.dto.workflow.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "JobDependencyResponse")
public class JobDependencyResponse {

    private Long id;

    private Long workflowId;

    private Long jobId;

    private Long parentJobId;
}
