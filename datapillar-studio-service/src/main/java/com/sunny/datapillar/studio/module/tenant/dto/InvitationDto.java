package com.sunny.datapillar.studio.module.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

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
        @Size(max = 128, message = "邮箱长度不能超过128个字符")
        private String inviteeEmail;

        @Size(max = 32, message = "手机号长度不能超过32个字符")
        private String inviteeMobile;

        @NotEmpty(message = "角色不能为空")
        private List<Long> roleIds;

        private LocalDateTime expiresAt;
    }

    @Data
    @Schema(name = "InvitationCreateResponse")
    public static class CreateResponse {
        private Long invitationId;
        private String inviteCode;
        private LocalDateTime expiresAt;
    }

    @Data
    @Schema(name = "InvitationResponse")
    public static class Response {
        private Long id;
        private Long tenantId;
        private String inviteeEmail;
        private String inviteeMobile;
        private Integer status;
        private String inviteCode;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Schema(name = "InvitationActionRequest")
    public static class ActionRequest {
        @NotBlank(message = "操作不能为空")
        private String action;
    }

    @Data
    @Schema(name = "InvitationAcceptRequest")
    public static class AcceptRequest {
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 64, message = "邀请码长度不能超过64个字符")
        private String inviteCode;
    }
}
