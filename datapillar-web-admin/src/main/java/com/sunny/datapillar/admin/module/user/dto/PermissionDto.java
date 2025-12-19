package com.sunny.datapillar.admin.module.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 权限 DTO
 *
 * @author sunny
 */
public class PermissionDto {

    @Data
    public static class Response {
        private Long id;
        private String code;
        private String name;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
