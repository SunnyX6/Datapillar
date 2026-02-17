package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.security.SessionStateKeys;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 会话状态校验器
 * 负责会话状态校验逻辑与安全验证
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SessionStateVerifier {

    private final ReactiveStringRedisTemplate redisTemplate;

    public SessionStateVerifier(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAccessTokenActive(String sid, String jti) {
        if (sid == null || sid.isBlank() || jti == null || jti.isBlank()) {
            return Mono.just(false);
        }

        Mono<String> sessionStatus = redisTemplate.opsForValue()
                .get(SessionStateKeys.sessionStatusKey(sid))
                .defaultIfEmpty("");
        Mono<String> tokenStatus = redisTemplate.opsForValue()
                .get(SessionStateKeys.tokenStatusKey(jti))
                .defaultIfEmpty("");
        Mono<String> tokenSid = redisTemplate.opsForValue()
                .get(SessionStateKeys.tokenSessionKey(jti))
                .defaultIfEmpty("");

        return Mono.zip(sessionStatus, tokenStatus, tokenSid)
                .map(tuple -> SessionStateKeys.STATUS_ACTIVE.equals(tuple.getT1())
                        && SessionStateKeys.STATUS_ACTIVE.equals(tuple.getT2())
                        && sid.equals(tuple.getT3()));
    }
}

