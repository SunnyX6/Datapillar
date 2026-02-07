package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private final JwtUtil jwtUtil;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final long refreshTokenRememberExpiration;
    private final long loginTokenExpiration;

    public JwtTokenUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.refresh-token-expiration-remember:2592000}") long refreshTokenRememberExpiration,
            @Value("${jwt.login-token-expiration}") long loginTokenExpiration,
            @Value("${jwt.issuer}") String issuer) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT 密钥长度至少 32 位");
        }

        this.jwtUtil = new JwtUtil(secret, issuer);
        this.accessTokenExpiration = accessTokenExpiration * 1000;  // 转换为毫秒
        this.refreshTokenExpiration = refreshTokenExpiration * 1000;
        this.refreshTokenRememberExpiration = refreshTokenRememberExpiration * 1000;
        this.loginTokenExpiration = loginTokenExpiration * 1000;
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(Long userId, Long tenantId, String username, String email) {
        return generateAccessToken(userId, tenantId, username, email, null);
    }

    /**
     * 生成 Access Token（可附加扩展 claims）
     */
    public String generateAccessToken(Long userId, Long tenantId, String username, String email,
                                      Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tenantId", tenantId);
        claims.put("username", username);
        claims.put("email", email);
        claims.put("tokenType", "access");
        if (extraClaims != null && !extraClaims.isEmpty()) {
            claims.putAll(extraClaims);
        }

        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return jwtUtil.sign(claims, String.valueOf(userId), now, expiration);
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
        long refreshExpiration = rememberMe != null && rememberMe ? refreshTokenRememberExpiration : refreshTokenExpiration;
        Date expiration = new Date(now.getTime() + refreshExpiration);

        return jwtUtil.sign(claims, String.valueOf(userId), now, expiration);
    }

    /**
     * 生成登录态临时 Token（用于选择租户）
     */
    public String generateLoginToken(Long userId, Boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "login");
        claims.put("rememberMe", rememberMe != null && rememberMe);

        Date now = new Date();
        Date expiration = new Date(now.getTime() + loginTokenExpiration);

        return jwtUtil.sign(claims, String.valueOf(userId), now, expiration);
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return jwtUtil.parseToken(token);
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        return jwtUtil.isValid(token);
    }
    /**
     * 从 Token 提取 userId
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return jwtUtil.getUserId(claims);
    }

    /**
     * 从 Token 提取 tenantId
     */
    public Long getTenantId(String token) {
        Claims claims = parseToken(token);
        return jwtUtil.getTenantId(claims);
    }

    /**
     * 从 Token 提取 username
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return jwtUtil.getUsername(claims);
    }

    /**
     * 从 Token 提取 email
     */
    public String getEmail(String token) {
        Claims claims = parseToken(token);
        return jwtUtil.getEmail(claims);
    }

    /**
     * 获取 Token 类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return jwtUtil.getTokenType(claims);
    }

    /**
     * 从 Refresh Token 中提取 rememberMe 标志
     */
    public Boolean getRememberMe(String token) {
        Claims claims = parseToken(token);
        return jwtUtil.getRememberMe(claims);
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
        return jwtUtil.extractTokenSignature(token);
    }

    /**
     * 获取 Access Token 过期时间（秒）
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration / 1000;
    }

    /**
     * 获取 Refresh Token 过期时间（秒）
     */
    public long getRefreshTokenExpiration(boolean rememberMe) {
        long expiration = rememberMe ? refreshTokenRememberExpiration : refreshTokenExpiration;
        return expiration / 1000;
    }
}
