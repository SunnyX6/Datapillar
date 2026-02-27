package com.sunny.datapillar.studio.dto.workflow.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "WorkflowListItem")
public class WorkflowListItemResponse {

    private Long id;

    private Long projectId;

    private String projectName;

    private String workflowName;

    private Integer triggerType;

    private Integer status;

    private String description;

    private Integer jobCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
