package com.sunny.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT 工具类（仅用于验证，不颁发 Token）
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final String issuer;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        this.issuer = issuer;
    }

    /**
     * 验证并解析 Token
     */
    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean isValid(String token) {
        try {
            Claims claims = validateAndParse(token);
            return claims.getExpiration().getTime() > System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = validateAndParse(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 获取用户名
     */
    public String getUsername(String token) {
        Claims claims = validateAndParse(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 获取邮箱
     */
    public String getEmail(String token) {
        Claims claims = validateAndParse(token);
        return claims.get("email", String.class);
    }

    /**
     * 获取 Token 类型（access/refresh）
     */
    public String getTokenType(String token) {
        Claims claims = validateAndParse(token);
        return claims.get("tokenType", String.class);
    }
}
