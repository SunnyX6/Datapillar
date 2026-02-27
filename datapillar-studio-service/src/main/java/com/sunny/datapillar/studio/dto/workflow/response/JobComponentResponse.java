package com.sunny.datapillar.studio.dto.workflow.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "JobComponentResponse")
public class JobComponentResponse {

    private Long id;

    private String componentCode;

    private String componentName;

    private String componentType;

    private Map<String, Object> jobParams;

    private String description;

    private String icon;

    private String color;

    private Integer sortOrder;
}
