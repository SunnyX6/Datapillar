package com.sunny.datapillar.admin.module.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 菜单 DTO
 *
 * @author sunny
 */
public class MenuDto {

    @Data
    public static class Response {
        private Long id;
        private Long parentId;
        private String name;
        private String path;
        private String component;
        private String icon;
        private String permissionCode;
        private Integer visible;
        private Integer sort;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<Response> children;
    }
}
