package com.sunny.datapillar.auth.service.login.method.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 单点登录状态存储
 * 负责单点登录状态状态存储与生命周期管理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SsoStateStore {

    private static final String PAYLOAD_KEY_PREFIX = "sso:state:payload:";
    private static final String ISSUED_KEY_PREFIX = "sso:state:issued:";
    private static final String CONSUMED_KEY_PREFIX = "sso:state:consumed:";

    private static final String RESULT_INVALID = "__INVALID__";
    private static final String RESULT_EXPIRED = "__EXPIRED__";
    private static final String RESULT_REPLAYED = "__REPLAYED__";

    private static final int MIN_STATE_BYTES = 16;

    private static final String CONSUME_SCRIPT = "local payload=redis.call('GET', KEYS[1]); "
            + "if payload then redis.call('DEL', KEYS[1]); redis.call('SETEX', KEYS[2], ARGV[1], '1'); return payload; end; "
            + "if redis.call('EXISTS', KEYS[2]) == 1 then return '" + RESULT_REPLAYED + "'; end; "
            + "if redis.call('EXISTS', KEYS[3]) == 1 then return '" + RESULT_EXPIRED + "'; end; "
            + "return '" + RESULT_INVALID + "';";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder STATE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> consumeScript;

    @Value("${sso.state-ttl-seconds:300}")
    private long stateTtlSeconds;

    @Value("${sso.state-replay-ttl-seconds:3600}")
    private long replayStateTtlSeconds;

    @Value("${sso.state-bytes:24}")
    private int stateBytes;

    public SsoStateStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
    }

    @PostConstruct
    public void validateStateBytesConfig() {
        if (stateBytes < MIN_STATE_BYTES) {
            throw new IllegalStateException("sso.state-bytes must be >= " + MIN_STATE_BYTES);
        }
    }

    public String createState(Long tenantId, String provider) {
        validateStateBytesConfig();
        String normalized = normalize(provider);
        long issuedTtlSeconds = Math.max(replayStateTtlSeconds, stateTtlSeconds + 60);
        for (int i = 0; i < 5; i++) {
            String state = generateState();
            String payloadKey = buildPayloadKey(state);
            String issuedKey = buildIssuedKey(state);
            StatePayload payload = new StatePayload(tenantId, normalized);
            try {
                String value = objectMapper.writeValueAsString(payload);
                Boolean created = stringRedisTemplate.opsForValue()
                        .setIfAbsent(payloadKey, value, Duration.ofSeconds(stateTtlSeconds));
                if (Boolean.TRUE.equals(created)) {
                    stringRedisTemplate.opsForValue().set(issuedKey, "1", Duration.ofSeconds(issuedTtlSeconds));
                    return state;
                }
            } catch (Exception ex) {
                throw new com.sunny.datapillar.common.exception.InternalException(ex, "SSO state 生成失败");
            }
        }
        throw new com.sunny.datapillar.common.exception.InternalException("SSO state 生成失败");
    }

    public StatePayload consumeOrThrow(String state, Long expectedTenantId, String expectedProvider) {
        if (state == null || state.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 无效");
        }
        String result = stringRedisTemplate.execute(
                consumeScript,
                List.of(buildPayloadKey(state), buildConsumedKey(state), buildIssuedKey(state)),
                String.valueOf(Math.max(replayStateTtlSeconds, 1L))
        );

        if (result == null || RESULT_INVALID.equals(result)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 无效");
        }
        if (RESULT_EXPIRED.equals(result)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 已过期");
        }
        if (RESULT_REPLAYED.equals(result)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 已被重复使用");
        }

        StatePayload payload;
        try {
            payload = objectMapper.readValue(result, StatePayload.class);
        } catch (Exception ex) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException(ex, "SSO state 无效");
        }

        if (expectedTenantId != null && (payload.getTenantId() == null || !expectedTenantId.equals(payload.getTenantId()))) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 无效");
        }
        String normalizedExpectedProvider = normalize(expectedProvider);
        if (normalizedExpectedProvider != null && !normalizedExpectedProvider.equals(payload.getProvider())) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 无效");
        }
        return payload;
    }

    private String generateState() {
        byte[] randomBytes = new byte[stateBytes];
        SECURE_RANDOM.nextBytes(randomBytes);
        return STATE_ENCODER.encodeToString(randomBytes);
    }

    private String buildPayloadKey(String state) {
        return PAYLOAD_KEY_PREFIX + state;
    }

    private String buildIssuedKey(String state) {
        return ISSUED_KEY_PREFIX + state;
    }

    private String buildConsumedKey(String state) {
        return CONSUMED_KEY_PREFIX + state;
    }

    private String normalize(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
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
