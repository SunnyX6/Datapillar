package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.common.utils.JwtUtil;
import com.sunny.datapillar.common.security.SessionTokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/**
 * JWT令牌组件
 * 负责JWT令牌核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Component
public class JwtToken {

    private final JwtUtil jwtUtil;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final long refreshTokenRememberExpiration;
    private final long loginTokenExpiration;

    public JwtToken(
            JwtUtil jwtUtil,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.refresh-token-expiration-remember:2592000}") long refreshTokenRememberExpiration,
            @Value("${jwt.login-token-expiration}") long loginTokenExpiration) {

        this.jwtUtil = jwtUtil;
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

        String sid = normalizeClaim(claims.get(SessionTokenClaims.SESSION_ID));
        String jti = normalizeClaim(claims.get(SessionTokenClaims.TOKEN_ID));
        if (sid == null) {
            sid = UUID.randomUUID().toString();
        }
        if (jti == null) {
            jti = UUID.randomUUID().toString();
        }
        claims.put(SessionTokenClaims.SESSION_ID, sid);
        claims.put(SessionTokenClaims.TOKEN_ID, jti);

        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return jwtUtil.sign(claims, String.valueOf(userId), now, expiration, jti);
    }

    /**
     * 生成 Refresh Token
     * @param userId 用户ID
     * @param rememberMe 是否记住我（true=30天，false=7天）
     */
    public String generateRefreshToken(Long userId, Long tenantId, Boolean rememberMe) {
        return generateRefreshToken(userId, tenantId, rememberMe, null, null);
    }

    public String generateRefreshToken(Long userId, Long tenantId, Boolean rememberMe,
                                       String sessionId, String tokenId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");
        claims.put("tenantId", tenantId);
        claims.put("rememberMe", rememberMe != null && rememberMe);

        String sid = normalizeClaim(sessionId);
        String jti = normalizeClaim(tokenId);
        if (sid == null) {
            sid = UUID.randomUUID().toString();
        }
        if (jti == null) {
            jti = UUID.randomUUID().toString();
        }
        claims.put(SessionTokenClaims.SESSION_ID, sid);
        claims.put(SessionTokenClaims.TOKEN_ID, jti);

        Date now = new Date();
        long refreshExpiration = rememberMe != null && rememberMe ? refreshTokenRememberExpiration : refreshTokenExpiration;
        Date expiration = new Date(now.getTime() + refreshExpiration);

        return jwtUtil.sign(claims, String.valueOf(userId), now, expiration, jti);
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

    private String normalizeClaim(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
