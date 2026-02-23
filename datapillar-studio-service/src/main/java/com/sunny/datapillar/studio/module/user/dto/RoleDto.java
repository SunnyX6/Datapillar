package com.sunny.datapillar.studio.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色数据传输对象
 * 定义角色数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class RoleDto {

    @Data
    @Schema(name = "RoleCreate")
    public static class Create {
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        private String name;

        @Size(max = 255, message = "角色描述长度不能超过255个字符")
        private String description;

        @Size(max = 16, message = "角色类型长度不能超过16个字符")
        private String type;
    }

    @Data
    @Schema(name = "RoleUpdate")
    public static class Update {
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        private String name;

        @Size(max = 255, message = "角色描述长度不能超过255个字符")
        private String description;

        @Size(max = 16, message = "角色类型长度不能超过16个字符")
        private String type;
    }

    @Data
    @Schema(name = "RoleResponse")
    public static class Response {
        private Long id;
        private Long tenantId;
        private String type;
        private String name;
        private String description;
        private Integer status;
        private Integer sort;
        private Integer isBuiltin;
        private Long memberCount;
    }

    @Data
    @Schema(name = "RoleMemberItem")
    public static class MemberItem {
        private Long userId;
        private String username;
        private String nickname;
        private String email;
        private String phone;
        private Integer memberStatus;
        private LocalDateTime joinedAt;
        private LocalDateTime assignedAt;
    }

    @Data
    @Schema(name = "RoleMembersResponse")
    public static class MembersResponse {
        private Long roleId;
        private String roleName;
        private String roleType;
        private Integer roleStatus;
        private Integer roleBuiltin;
        private Long memberCount;
        private List<MemberItem> members;
    }
}
