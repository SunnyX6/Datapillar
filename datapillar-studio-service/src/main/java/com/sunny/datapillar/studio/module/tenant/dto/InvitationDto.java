package com.sunny.datapillar.studio.module.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 邀请数据传输对象
 * 定义邀请数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class InvitationDto {

    @Data
    @Schema(name = "InvitationCreate")
    public static class Create {
        @NotNull(message = "角色不能为空")
        private Long roleId;

        @NotNull(message = "过期时间不能为空")
        private OffsetDateTime expiresAt;
    }

    @Data
    @Schema(name = "InvitationCreateResponse")
    public static class CreateResponse {
        private Long invitationId;
        private String inviteCode;
        private String inviteUri;
        private OffsetDateTime expiresAt;
        private String tenantName;
        private Long roleId;
        private String roleName;
        private String inviterName;
    }

    @Data
    @Schema(name = "InvitationDetailResponse")
    public static class DetailResponse {
        private String inviteCode;
        private String tenantName;
        private Long roleId;
        private String roleName;
        private String inviterName;
        private OffsetDateTime expiresAt;
        private Integer status;
    }

    @Data
    @Schema(name = "InvitationRegisterRequest")
    public static class RegisterRequest {
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 64, message = "邀请码长度不能超过64个字符")
        private String inviteCode;

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        private String username;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过128个字符")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 255, message = "密码长度必须在6-255个字符之间")
        private String password;
    }
}
