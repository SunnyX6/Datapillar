package com.sunny.admin.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限响应DTO
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
public class PermissionRespDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
