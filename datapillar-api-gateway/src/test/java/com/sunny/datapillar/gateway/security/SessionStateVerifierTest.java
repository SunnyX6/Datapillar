package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.security.SessionStateKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionStateVerifierTest {

    @Test
    void isAccessTokenActive_shouldReturnTrueWhenSessionAndTokenActive() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(SessionStateKeys.sessionStatusKey("sid-1"))).thenReturn(Mono.just(SessionStateKeys.STATUS_ACTIVE));
        when(valueOperations.get(SessionStateKeys.tokenStatusKey("jti-1"))).thenReturn(Mono.just(SessionStateKeys.STATUS_ACTIVE));
        when(valueOperations.get(SessionStateKeys.tokenSessionKey("jti-1"))).thenReturn(Mono.just("sid-1"));

        SessionStateVerifier verifier = new SessionStateVerifier(redisTemplate);
        Boolean active = verifier.isAccessTokenActive("sid-1", "jti-1").block();

        assertTrue(Boolean.TRUE.equals(active));
    }

    @Test
    void isAccessTokenActive_shouldReturnFalseWhenTokenBelongsToAnotherSession() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(SessionStateKeys.sessionStatusKey("sid-1"))).thenReturn(Mono.just(SessionStateKeys.STATUS_ACTIVE));
        when(valueOperations.get(SessionStateKeys.tokenStatusKey("jti-1"))).thenReturn(Mono.just(SessionStateKeys.STATUS_ACTIVE));
        when(valueOperations.get(SessionStateKeys.tokenSessionKey("jti-1"))).thenReturn(Mono.just("sid-2"));

        SessionStateVerifier verifier = new SessionStateVerifier(redisTemplate);
        Boolean active = verifier.isAccessTokenActive("sid-1", "jti-1").block();

        assertFalse(Boolean.TRUE.equals(active));
    }
}

