package com.sunny.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 Token 响应（标准格式）
 * 符合 RFC 6749 规范
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2TokenRespDto {
    /**
     * Access Token（JWT）
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * Token 类型，固定为 "Bearer"
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Access Token 过期时间（秒）
     */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /**
     * Refresh Token（可选）
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * Scope（可选，预留）
     */
    @JsonProperty("scope")
    private String scope;
}
