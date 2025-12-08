package com.sunny.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO Token 验证请求（供外部系统如XXL-Job调用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoValidateReqDto {
    /**
     * JWT Access Token
     */
    private String token;
}
