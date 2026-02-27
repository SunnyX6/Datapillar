package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "JobUpdate")
public class JobUpdateRequest {

    private String jobName;

    private Long jobType;

    private Map<String, Object> jobParams;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer retryInterval;

    private Integer priority;

    private Double positionX;

    private Double positionY;

    private String description;
}
