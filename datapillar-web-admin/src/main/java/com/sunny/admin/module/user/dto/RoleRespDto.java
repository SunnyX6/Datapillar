package com.sunny.admin.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色响应DTO
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
public class RoleRespDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 角色权限列表
     */
    private List<PermissionRespDto> permissions;
}
