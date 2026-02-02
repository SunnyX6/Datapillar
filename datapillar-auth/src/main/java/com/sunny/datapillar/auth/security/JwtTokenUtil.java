package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;

    public JwtTokenUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.issuer}") String issuer) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT 密钥长度至少 32 位");
        }

        this.key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        this.accessTokenExpiration = accessTokenExpiration * 1000;  // 转换为毫秒
        this.refreshTokenExpiration = refreshTokenExpiration * 1000;
        this.issuer = issuer;
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(Long userId, Long tenantId, String username, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tenantId", tenantId);
        claims.put("username", username);
        claims.put("email", email);
        claims.put("tokenType", "access");

        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 生成 Refresh Token
     * @param userId 用户ID
     * @param rememberMe 是否记住我（true=30天，false=7天）
     */
    public String generateRefreshToken(Long userId, Long tenantId, Boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");
        claims.put("tenantId", tenantId);
        claims.put("rememberMe", rememberMe != null && rememberMe);

        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, e.getMessage());
        }
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !isExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 Token 是否过期
     */
    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /**
     * 从 Token 提取 userId
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 提取 tenantId
     */
    public Long getTenantId(String token) {
        Claims claims = parseToken(token);
        Object tenantId = claims.get("tenantId");
        if (tenantId instanceof Number) {
            return ((Number) tenantId).longValue();
        }
        if (tenantId instanceof String) {
            return Long.parseLong((String) tenantId);
        }
        return null;
    }

    /**
     * 从 Token 提取 username
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 提取 email
     */
    public String getEmail(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    /**
     * 获取 Token 类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }

    /**
     * 从 Refresh Token 中提取 rememberMe 标志
     */
    public Boolean getRememberMe(String token) {
        Claims claims = parseToken(token);
        return claims.get("rememberMe", Boolean.class);
    }

    /**
     * 提取 Token 签名（用于SSO存储）
     * JWT格式: header.payload.signature
     * 我们只存储 signature 部分
     */
    public String extractTokenSignature(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "JWT 格式非法");
        }

        // 返回签名部分（第三部分）
        return parts[2];
    }

    /**
     * 获取 Access Token 过期时间（秒）
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration / 1000;
    }
}
