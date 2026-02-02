package com.sunny.datapillar.admin.module.user.dto;

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
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        private String name;

        @Size(max = 255, message = "角色描述长度不能超过255个字符")
        private String description;

        @Size(max = 16, message = "角色类型长度不能超过16个字符")
        private String type;

        private List<PermissionObjectDto.Assignment> permissions;
    }

    @Data
    public static class Update {
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        private String name;

        @Size(max = 255, message = "角色描述长度不能超过255个字符")
        private String description;

        @Size(max = 16, message = "角色类型长度不能超过16个字符")
        private String type;

        private List<PermissionObjectDto.Assignment> permissions;
    }

    @Data
    public static class Response {
        private Long id;
        private String type;
        private String name;
        private String description;
        private Integer status;
        private Integer sort;
    }
}
