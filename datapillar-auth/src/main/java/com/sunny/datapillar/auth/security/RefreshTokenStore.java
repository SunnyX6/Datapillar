package com.sunny.datapillar.auth.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:token:";

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void store(Long tenantId, Long userId, String refreshToken, long ttlSeconds) {
        String key = buildKey(tenantId, userId);
        String hashed = TokenHashUtil.sha256(refreshToken);
        stringRedisTemplate.opsForValue().set(key, hashed, Duration.ofSeconds(ttlSeconds));
    }

    public boolean validate(Long tenantId, Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        String key = buildKey(tenantId, userId);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached == null || cached.isBlank()) {
            return false;
        }
        String hashed = TokenHashUtil.sha256(refreshToken);
        return cached.equals(hashed);
    }

    public void clear(Long tenantId, Long userId) {
        String key = buildKey(tenantId, userId);
        stringRedisTemplate.delete(key);
    }

    private String buildKey(Long tenantId, Long userId) {
        return KEY_PREFIX + tenantId + ":" + userId;
    }
}
