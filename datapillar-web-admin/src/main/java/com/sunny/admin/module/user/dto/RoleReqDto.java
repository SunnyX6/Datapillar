package com.sunny.admin.module.user.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 角色请求DTO
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
public class RoleReqDto {

    @NotBlank(message = "角色代码不能为空")
    @Size(max = 64, message = "角色代码长度不能超过64个字符")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过64个字符")
    private String name;

    @Size(max = 255, message = "角色描述长度不能超过255个字符")
    private String description;

    /**
     * 权限ID列表
     */
    private List<Long> permissionIds;
}
