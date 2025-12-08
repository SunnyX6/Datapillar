package com.sunny.admin.module.projects.dto;

import com.sunny.admin.module.projects.enums.ProjectStatus;
import lombok.Data;

/**
 * 查询项目请求DTO
 */
@Data
public class ProjectQueryReqDto {

    /**
     * 搜索关键词（项目名称或描述）
     */
    private String keyword;

    /**
     * 项目状态
     */
    private ProjectStatus status;

    /**
     * 是否只显示收藏的项目
     */
    private Boolean onlyFavorites;

    /**
     * 是否只显示可见的项目
     */
    private Boolean onlyVisible;

    /**
     * 页码（从1开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 排序字段
     */
    private String sortBy = "updatedAt";

    /**
     * 排序方向（asc/desc）
     */
    private String sortOrder = "desc";
}
