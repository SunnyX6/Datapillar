package com.sunny.admin.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应DTO
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
public class UserRespDto {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 用户角色列表
     */
    private List<RoleRespDto> roles;

    /**
     * 用户权限列表
     */
    private List<String> permissions;
}
