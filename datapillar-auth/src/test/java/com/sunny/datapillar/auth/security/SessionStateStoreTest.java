package com.sunny.datapillar.auth.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.security.SessionStateKeys;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class SessionStateStoreTest {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @Test
  void rotateForRefresh_shouldReturnRefreshReused() {
    when(stringRedisTemplate.execute(
            any(DefaultRedisScript.class),
            any(List.class),
            eq(SessionStateKeys.STATUS_ACTIVE),
            eq("refresh-old"),
            eq("refresh-new"),
            eq("access-new"),
            eq("120")))
        .thenReturn("__REFRESH_REUSED__");

    SessionStateStore store = new SessionStateStore(stringRedisTemplate);
    SessionStateStore.RotateResult result =
        store.rotateForRefresh("sid-1", "refresh-old", "refresh-new", "access-new", 120, 30);

    assertFalse(result.success());
    assertTrue(result.refreshReused());
    assertFalse(result.sessionInactive());
  }

  @Test
  void rotateForRefresh_shouldReturnSessionInactive() {
    when(stringRedisTemplate.execute(
            any(DefaultRedisScript.class),
            any(List.class),
            eq(SessionStateKeys.STATUS_ACTIVE),
            eq("refresh-old"),
            eq("refresh-new"),
            eq("access-new"),
            eq("120")))
        .thenReturn("__SESSION_INACTIVE__");

    SessionStateStore store = new SessionStateStore(stringRedisTemplate);
    SessionStateStore.RotateResult result =
        store.rotateForRefresh("sid-1", "refresh-old", "refresh-new", "access-new", 120, 30);

    assertFalse(result.success());
    assertTrue(result.sessionInactive());
    assertFalse(result.refreshReused());
  }

  @Test
  void rotateForRefresh_shouldActivateNewTokenAndRevokeOldToken() {
    when(stringRedisTemplate.execute(
            any(DefaultRedisScript.class),
            any(List.class),
            eq(SessionStateKeys.STATUS_ACTIVE),
            eq("refresh-old"),
            eq("refresh-new"),
            eq("access-new"),
            eq("120")))
        .thenReturn("access-old");
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(stringRedisTemplate.getExpire(
            SessionStateKeys.tokenStatusKey("access-old"), TimeUnit.SECONDS))
        .thenReturn(30L);

    SessionStateStore store = new SessionStateStore(stringRedisTemplate);
    SessionStateStore.RotateResult result =
        store.rotateForRefresh("sid-1", "refresh-old", "refresh-new", "access-new", 120, 30);

    assertTrue(result.success());
    verify(valueOperations)
        .set(
            SessionStateKeys.tokenStatusKey("access-new"),
            SessionStateKeys.STATUS_ACTIVE,
            Duration.ofSeconds(30));
    verify(valueOperations)
        .set(SessionStateKeys.tokenSessionKey("access-new"), "sid-1", Duration.ofSeconds(30));
    verify(valueOperations)
        .set(
            SessionStateKeys.tokenStatusKey("access-old"),
            SessionStateKeys.STATUS_REVOKED,
            Duration.ofSeconds(30));
  }
}
