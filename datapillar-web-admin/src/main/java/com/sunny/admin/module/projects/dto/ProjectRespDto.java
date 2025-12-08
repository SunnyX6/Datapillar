package com.sunny.admin.module.projects.dto;

import com.sunny.admin.module.projects.enums.ProjectStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目响应DTO
 */
@Data
public class ProjectRespDto {

    /**
     * 项目ID
     */
    private Long id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目所有者ID
     */
    private Long ownerId;

    /**
     * 项目所有者名称
     */
    private String ownerName;

    /**
     * 项目状态
     */
    private ProjectStatus status;

    /**
     * 项目标签
     */
    private List<String> tags;

    /**
     * 是否收藏
     */
    private Boolean isFavorite;

    /**
     * 是否可见
     */
    private Boolean isVisible;

    /**
     * 成员数量
     */
    private Integer memberCount;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
