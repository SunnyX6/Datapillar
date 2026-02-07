package com.sunny.datapillar.gateway.util;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 工具类（仅用于验证，不颁发 Token）
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Component
public class JwtUtil {

    private final com.sunny.datapillar.common.utils.JwtUtil jwtUtil;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.jwtUtil = new com.sunny.datapillar.common.utils.JwtUtil(secret, issuer);
    }

    /**
     * 验证并解析 Token
     */
    public Claims validateAndParse(String token) {
        return jwtUtil.parseTokenWithIssuer(token);
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean isValid(String token) {
        return jwtUtil.isValidWithIssuer(token);
    }

    /**
     * 从 Token 获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getUserId(claims);
    }

    /**
     * 从 Token 获取用户名
     */
    public String getUsername(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getUsername(claims);
    }

    /**
     * 从 Token 获取租户ID
     */
    public Long getTenantId(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getTenantId(claims);
    }

    /**
     * 从 Token 获取邮箱
     */
    public String getEmail(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getEmail(claims);
    }

    /**
     * 获取 Token 类型（access/refresh）
     */
    public String getTokenType(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getTokenType(claims);
    }

    /**
     * 从 Token 获取平台超管审计字段
     */
    public Long getActorUserId(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getActorUserId(claims);
    }

    public Long getActorTenantId(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.getActorTenantId(claims);
    }

    public boolean isImpersonation(String token) {
        Claims claims = validateAndParse(token);
        return jwtUtil.isImpersonation(claims);
    }
}
