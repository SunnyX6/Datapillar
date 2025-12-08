package com.sunny.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 Token 请求（供Gravitino使用）
 * 支持标准的OAuth2 Password Grant Type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2TokenReqDto {
    /**
     * Grant type, 必须是 "password"
     */
    private String grantType;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 客户端ID（可选，预留）
     */
    private String clientId;

    /**
     * 客户端密钥（可选，预留）
     */
    private String clientSecret;
}
