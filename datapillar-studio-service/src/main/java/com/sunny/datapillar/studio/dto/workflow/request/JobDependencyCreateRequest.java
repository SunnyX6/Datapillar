package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "JobDependencyCreate")
public class JobDependencyCreateRequest {

    @NotNull(message = "任务 ID 不能为空")
    private Long jobId;

    @NotNull(message = "上游任务 ID 不能为空")
    private Long parentJobId;
}
