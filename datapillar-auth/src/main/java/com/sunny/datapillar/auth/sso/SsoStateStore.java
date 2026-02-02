package com.sunny.datapillar.auth.sso;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SSO state 存储（Redis）
 */
@Component
public class SsoStateStore {

    private static final String KEY_PREFIX = "sso:state:";
    private static final String CONSUME_SCRIPT = "local v=redis.call('GET', KEYS[1]); "
            + "if v then redis.call('DEL', KEYS[1]); end; return v;";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> consumeScript;

    @Value("${sso.state-ttl-seconds:300}")
    private long stateTtlSeconds;

    public SsoStateStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
    }

    public String createState(Long tenantId, String provider) {
        String normalized = normalize(provider);
        for (int i = 0; i < 5; i++) {
            String state = UUID.randomUUID().toString().replace("-", "");
            String key = buildKey(state);
            StatePayload payload = new StatePayload(tenantId, normalized);
            try {
                String value = objectMapper.writeValueAsString(payload);
                Boolean ok = stringRedisTemplate.opsForValue()
                        .setIfAbsent(key, value, Duration.ofSeconds(stateTtlSeconds));
                if (Boolean.TRUE.equals(ok)) {
                    return state;
                }
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.AUTH_SSO_STATE_GENERATE_FAILED);
            }
        }
        throw new BusinessException(ErrorCode.AUTH_SSO_STATE_GENERATE_FAILED);
    }

    public StatePayload consumeState(String state) {
        String key = buildKey(state);
        String value = stringRedisTemplate.execute(consumeScript, List.of(key));
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_SSO_STATE_INVALID);
        }
        try {
            return objectMapper.readValue(value, StatePayload.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_SSO_STATE_INVALID);
        }
    }

    private String buildKey(String state) {
        return KEY_PREFIX + state;
    }

    private String normalize(String provider) {
        if (provider == null) {
            return "";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    @Data
    @AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class StatePayload {
        private Long tenantId;
        private String provider;
    }
}
