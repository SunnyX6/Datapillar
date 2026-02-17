package com.sunny.datapillar.auth.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.auth.util.HashUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
/**
 * CSRF令牌存储
 * 负责CSRF令牌状态存储与生命周期管理
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Component
public class CsrfTokenStore {

    private static final String KEY_PREFIX = "auth:csrf:token:";
    private static final String REFRESH_KEY_PREFIX = "auth:csrf:refresh:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthSecurityProperties securityProperties;

    public CsrfTokenStore(StringRedisTemplate stringRedisTemplate, AuthSecurityProperties securityProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.securityProperties = securityProperties;
    }

    public String issueToken(Long tenantId, Long userId, long ttlSeconds) {
        return issueToken(KEY_PREFIX, tenantId, userId, ttlSeconds);
    }

    public String issueRefreshToken(Long tenantId, Long userId, long ttlSeconds) {
        return issueToken(REFRESH_KEY_PREFIX, tenantId, userId, ttlSeconds);
    }

    public boolean validateToken(Long tenantId, Long userId, String token) {
        return validateToken(KEY_PREFIX, tenantId, userId, token);
    }

    public boolean validateRefreshToken(Long tenantId, Long userId, String token) {
        return validateToken(REFRESH_KEY_PREFIX, tenantId, userId, token);
    }

    public void clearToken(Long tenantId, Long userId) {
        clearToken(KEY_PREFIX, tenantId, userId);
    }

    public void clearRefreshToken(Long tenantId, Long userId) {
        clearToken(REFRESH_KEY_PREFIX, tenantId, userId);
    }

    private String issueToken(String keyPrefix, Long tenantId, Long userId, long ttlSeconds) {
        String token = generateToken();
        String key = buildKey(keyPrefix, tenantId, userId);
        long effectiveTtlSeconds = ttlSeconds > 0 ? ttlSeconds : securityProperties.getCsrf().getTtlSeconds();
        String hashed = HashUtil.sha256(token);
        stringRedisTemplate.opsForValue().set(key, hashed, Duration.ofSeconds(effectiveTtlSeconds));
        return token;
    }

    private boolean validateToken(String keyPrefix, Long tenantId, Long userId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = buildKey(keyPrefix, tenantId, userId);
        String cachedHash = stringRedisTemplate.opsForValue().get(key);
        if (cachedHash == null || cachedHash.isBlank()) {
            return false;
        }
        String tokenHash = HashUtil.sha256(token);
        return cachedHash.equals(tokenHash);
    }

    private void clearToken(String keyPrefix, Long tenantId, Long userId) {
        String key = buildKey(keyPrefix, tenantId, userId);
        stringRedisTemplate.delete(key);
    }

    private String buildKey(String keyPrefix, Long tenantId, Long userId) {
        return keyPrefix + tenantId + ":" + userId;
    }

    private String generateToken() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
