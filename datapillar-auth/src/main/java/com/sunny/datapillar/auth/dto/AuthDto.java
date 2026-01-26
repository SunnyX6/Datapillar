package com.sunny.datapillar.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
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
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        /** 记住我（7天 vs 30天） */
        private Boolean rememberMe = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private Long userId;
        private String username;
        private String email;
        private List<String> roles;
        private List<String> permissions;
        private List<MenuInfo> menus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuInfo {
        private Long id;
        private String name;
        private String path;
        private String icon;
        private String permissionCode;
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
        private String username;
        private String email;
        private String errorMessage;

        public static TokenResponse success(Long userId, String username, String email) {
            TokenResponse dto = new TokenResponse();
            dto.setValid(true);
            dto.setUserId(userId);
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
        private String username;
    }

    // ==================== SSO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SsoValidateRequest {
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SsoValidateResponse {
        private Boolean valid;
        private Long userId;
        private String username;
        private String email;
        private List<String> roles;
        private String message;

        public static SsoValidateResponse success(Long userId, String username, String email) {
            return SsoValidateResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .build();
        }
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
