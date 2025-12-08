package com.sunny.admin.module.projects.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建项目请求DTO
 */
@Data
public class ProjectCreateReqDto {

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100个字符")
    private String name;

    /**
     * 项目描述
     */
    @Size(max = 500, message = "项目描述长度不能超过500个字符")
    private String description;

    /**
     * 项目标签
     */
    private List<String> tags;

    /**
     * 是否可见
     */
    private Boolean isVisible = true;
}
