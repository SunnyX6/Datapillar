package com.sunny.datapillar.studio.module.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 邀请 DTO
 */
public class InvitationDto {

    @Data
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
    public static class CreateResponse {
        private Long invitationId;
        private String inviteCode;
        private LocalDateTime expiresAt;
    }

    @Data
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
    public static class ActionRequest {
        @NotBlank(message = "操作不能为空")
        private String action;
    }
}
