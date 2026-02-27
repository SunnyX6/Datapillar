package com.sunny.datapillar.auth.service.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 登录令牌存储
 * 负责登录令牌状态存储与生命周期管理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class LoginTokenStore {

    private static final String TOKEN_PREFIX = "auth:login:token:";
    private static final String CONSUME_SCRIPT = "local value=redis.call('GET', KEYS[1]); "
            + "if value then redis.call('DEL', KEYS[1]); return value; end; "
            + "return nil;";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> consumeScript;

    @Value("${jwt.login-token-expiration:300}")
    private long ttlSeconds;

    public LoginTokenStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
    }

    public String issue(LoginTokenPayload payload) {
        if (payload == null || payload.getUserId() == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(token);
        try {
            String value = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(Math.max(1L, ttlSeconds)));
            return token;
        } catch (Exception ex) {
            throw new com.sunny.datapillar.common.exception.InternalException(ex, "服务器内部错误");
        }
    }

    public LoginTokenPayload consumeOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }
        String key = buildKey(token);
        String value = stringRedisTemplate.execute(consumeScript, List.of(key));
        if (value == null || value.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }
        try {
            return objectMapper.readValue(value, LoginTokenPayload.class);
        } catch (Exception ex) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException(ex, "Token无效");
        }
    }

    private String buildKey(String token) {
        return TOKEN_PREFIX + token;
    }

    public long ttlSeconds() {
        return Math.max(1L, ttlSeconds);
    }

    @Data
    public static class LoginTokenPayload {
        private Long userId;
        private List<Long> tenantIds;
        private Boolean rememberMe;
        private String loginMethod;
    }
}
