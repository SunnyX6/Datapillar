package com.sunny.datapillar.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.auth.security.SessionStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisSessionStoreTest {

  @Mock private SessionStateStore delegate;

  @InjectMocks private RedisSessionStore redisSessionStore;

  @Test
  void rotateForRefresh_shouldMapDelegateResult() {
    when(delegate.rotateForRefresh("sid-1", "refresh-old", "refresh-new", "access-new", 100, 10))
        .thenReturn(SessionStateStore.RotateResult.withRefreshReused());

    SessionStore.RotateResult result =
        redisSessionStore.rotateForRefresh(
            "sid-1", "refresh-old", "refresh-new", "access-new", 100, 10);

    assertFalse(result.success());
    assertFalse(result.sessionInactive());
    assertTrue(result.refreshReused());
  }

  @Test
  void openSession_shouldDelegate() {
    redisSessionStore.openSession("sid-1", 10L, 1L, "access-1", "refresh-1", 100, 10);

    verify(delegate).openSession("sid-1", 10L, 1L, "access-1", "refresh-1", 100, 10);
  }

  @Test
  void rotateForRefresh_shouldKeepPreviousAccessJti() {
    when(delegate.rotateForRefresh("sid-1", "refresh-old", "refresh-new", "access-new", 100, 10))
        .thenReturn(SessionStateStore.RotateResult.success("access-old"));

    SessionStore.RotateResult result =
        redisSessionStore.rotateForRefresh(
            "sid-1", "refresh-old", "refresh-new", "access-new", 100, 10);

    assertTrue(result.success());
    assertEquals("access-old", result.previousAccessJti());
  }
}
