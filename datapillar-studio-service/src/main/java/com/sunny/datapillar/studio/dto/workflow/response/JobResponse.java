package com.sunny.datapillar.studio.dto.workflow.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "JobResponse")
public class JobResponse {

    private Long id;

    private Long workflowId;

    private String jobName;

    private Long jobType;

    private String jobTypeCode;

    private String jobTypeName;

    private Map<String, Object> jobParams;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer retryInterval;

    private Integer priority;

    private Double positionX;

    private Double positionY;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
