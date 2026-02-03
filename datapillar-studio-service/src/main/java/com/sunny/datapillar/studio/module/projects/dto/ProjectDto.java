package com.sunny.datapillar.studio.module.projects.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sunny.datapillar.studio.module.projects.enums.ProjectStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 项目 DTO
 *
 * @author sunny
 */
public class ProjectDto {

    @Data
    public static class Create {
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称长度不能超过100个字符")
        private String name;

        @Size(max = 500, message = "项目描述长度不能超过500个字符")
        private String description;

        private List<String> tags;

        private Boolean isVisible = true;
    }

    @Data
    public static class Update {
        @Size(max = 100, message = "项目名称长度不能超过100个字符")
        private String name;

        @Size(max = 500, message = "项目描述长度不能超过500个字符")
        private String description;

        private ProjectStatus status;

        private List<String> tags;

        private Boolean isFavorite;

        private Boolean isVisible;
    }

    @Data
    public static class Query {
        /** 搜索关键词（项目名称或描述） */
        private String keyword;

        private ProjectStatus status;

        /** 是否只显示收藏的项目 */
        private Boolean onlyFavorites;

        /** 是否只显示可见的项目 */
        private Boolean onlyVisible;

        private Integer page = 1;

        private Integer size = 10;

        private String sortBy = "updatedAt";

        private String sortOrder = "desc";
    }

    @Data
    public static class Response {
        private Long id;

        private String name;

        private String description;

        private Long ownerId;

        private String ownerName;

        private ProjectStatus status;

        private List<String> tags;

        private Boolean isFavorite;

        private Boolean isVisible;

        private Integer memberCount;

        private LocalDateTime lastAccessedAt;

        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;
    }
}
