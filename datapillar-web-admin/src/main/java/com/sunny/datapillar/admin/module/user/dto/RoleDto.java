package com.sunny.datapillar.admin.module.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色 DTO
 *
 * @author sunny
 */
public class RoleDto {

    @Data
    public static class Create {
        @NotBlank(message = "角色代码不能为空")
        @Size(max = 64, message = "角色代码长度不能超过64个字符")
        private String code;

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        private String name;

        @Size(max = 255, message = "角色描述长度不能超过255个字符")
        private String description;

        private List<Long> permissionIds;
    }

    @Data
    public static class Update {
        @Size(max = 64, message = "角色代码长度不能超过64个字符")
        private String code;

        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        private String name;

        @Size(max = 255, message = "角色描述长度不能超过255个字符")
        private String description;

        private List<Long> permissionIds;
    }

    @Data
    public static class Response {
        private Long id;
        private String code;
        private String name;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<PermissionDto.Response> permissions;
    }
}
