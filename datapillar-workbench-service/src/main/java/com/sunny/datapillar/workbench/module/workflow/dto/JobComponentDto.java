package com.sunny.datapillar.workbench.module.workflow.dto;

import java.util.Map;

import lombok.Data;

/**
 * 组件 DTO
 *
 * @author sunny
 */
public class JobComponentDto {

    @Data
    public static class Response {
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
}
