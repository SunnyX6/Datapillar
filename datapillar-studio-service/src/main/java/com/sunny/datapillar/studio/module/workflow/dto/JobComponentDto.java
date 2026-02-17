package com.sunny.datapillar.studio.module.workflow.dto;

import java.util.Map;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务Component数据传输对象
 * 定义任务Component数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class JobComponentDto {

    @Data
    @Schema(name = "JobComponentResponse")
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
