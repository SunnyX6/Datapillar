package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "JobCreate")
public class JobCreateRequest {

    @NotBlank(message = "任务名称不能为空")
    private String jobName;

    @NotNull(message = "任务类型不能为空")
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
