package com.sunny.datapillar.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 认证数据传输对象
 * 定义认证数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class AuthDto {

    // ==================== 登录 ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AuthLoginResponse")
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AuthLoginResult")
    public static class LoginResult {
        /** 仅在需要租户选择时返回 TENANT_SELECT */
        private String loginStage;
        /** 可用租户列表；SUCCESS 场景下第一个为当前租户 */
        private List<TenantOption> tenants;

        private Long userId;
        private String username;
        private String email;
        private List<RoleInfo> roles;
        private List<MenuInfo> menus;
    }

    @Data
    @Schema(name = "AuthLoginFlowRequest")
    public static class LoginFlowRequest {
        @NotBlank(message = "stage 不能为空")
        private String stage;

        private Boolean rememberMe;

        private String loginAlias;

        private String password;

        private String tenantCode;

        private String provider;

        private String code;

        private String state;

        private Long tenantId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AuthTenantOption")
    public static class TenantOption {
        private Long tenantId;
        private String tenantCode;
        private String tenantName;
        private Integer status;
        private Integer isDefault;
    }

    @Data
    @Schema(name = "AuthLoginTenantRequest")
    public static class LoginTenantRequest {
        @NotBlank(message = "loginToken 不能为空")
        private String loginToken;

        @NotNull(message = "tenantId 不能为空")
        private Long tenantId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AuthRoleInfo")
    public static class RoleInfo {
        private Long id;
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AuthMenuInfo")
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
    @Schema(name = "AuthTokenRequest")
    public static class TokenRequest {
        @NotBlank(message = "Token 不能为空")
        private String token;

        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AuthTokenResponse")
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
    @Schema(name = "AuthTokenInfo")
    public static class TokenInfo {
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
    @Schema(name = "AuthSsoQrResponse")
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
    @Schema(name = "AuthOAuth2TokenRequest")
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
    @Schema(name = "AuthOAuth2TokenResponse")
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
