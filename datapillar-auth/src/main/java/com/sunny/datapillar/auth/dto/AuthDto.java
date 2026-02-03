package com.sunny.datapillar.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证 DTO
 *
 * @author sunny
 */
public class AuthDto {

    // ==================== 登录 ====================

    @Data
    public static class LoginRequest {
        private String tenantCode;

        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        /** 记住我（7天 vs 30天） */
        private Boolean rememberMe = false;

        /** 邀请码（首次入库必填） */
        private String inviteCode;

        /** 邮箱（邀请匹配用，可选） */
        @Email(message = "邮箱格式不正确")
        private String email;

        /** 手机号（邀请匹配用，可选） */
        private String phone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private Long userId;
        private Long tenantId;
        private String username;
        private String email;
        private List<RoleInfo> roles;
        private List<MenuInfo> menus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResult {
        /** SUCCESS / TENANT_SELECT */
        private String loginStage;
        /** 多租户选择时返回 */
        private String loginToken;
        /** 多租户选择时返回 */
        private List<TenantOption> tenants;

        private Long userId;
        private Long tenantId;
        private String username;
        private String email;
        private List<RoleInfo> roles;
        private List<MenuInfo> menus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantOption {
        private Long tenantId;
        private String tenantCode;
        private String tenantName;
        private Integer status;
        private Integer isDefault;
    }

    @Data
    public static class LoginTenantRequest {
        @NotBlank(message = "loginToken 不能为空")
        private String loginToken;

        @NotNull(message = "tenantId 不能为空")
        private Long tenantId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private Long id;
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuInfo {
        private Long id;
        private String name;
        private String path;
        private String permissionCode;
        private String location;
        private Long categoryId;
        private String categoryName;
        private List<MenuInfo> children;
    }

    // ==================== Token ====================

    @Data
    public static class TokenRequest {
        @NotBlank(message = "Token 不能为空")
        private String token;

        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        private boolean valid;
        private Long userId;
        private Long tenantId;
        private String username;
        private String email;
        private String errorMessage;

        public static TokenResponse success(Long userId, Long tenantId, String username, String email) {
            TokenResponse dto = new TokenResponse();
            dto.setValid(true);
            dto.setUserId(userId);
            dto.setTenantId(tenantId);
            dto.setUsername(username);
            dto.setEmail(email);
            return dto;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenInfo {
        private Boolean valid;
        private Long remainingSeconds;
        private Long expirationTime;
        private Long issuedAt;
        private Long userId;
        private Long tenantId;
        private String username;
    }

    // ==================== SSO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SsoLoginRequest {
        @NotBlank(message = "租户编码不能为空")
        private String tenantCode;

        @NotBlank(message = "SSO 提供方不能为空")
        private String provider;

        @NotBlank(message = "授权码不能为空")
        private String authCode;

        @NotBlank(message = "state 不能为空")
        private String state;

        /** 邀请码（首次入库必填） */
        private String inviteCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SsoQrResponse {
        /** SDK / URL */
        private String type;

        /** 登录 state */
        private String state;

        /** 扫码/授权配置 */
        private java.util.Map<String, Object> payload;
    }

    // ==================== OAuth2 ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OAuth2TokenRequest {
        private String grantType;
        private String username;
        private String password;
        private String clientId;
        private String clientSecret;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OAuth2TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private Long expiresIn;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("scope")
        private String scope;
    }
}
