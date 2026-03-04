package com.sunny.datapillar.auth.session;

import com.sunny.datapillar.auth.security.SessionStateStore;
import org.springframework.stereotype.Component;

/** Redis implementation of session store. */
@Component
public class RedisSessionStore implements SessionStore {

  private final SessionStateStore delegate;

  public RedisSessionStore(SessionStateStore delegate) {
    this.delegate = delegate;
  }

  @Override
  public void openSession(
      String sid,
      Long tenantId,
      Long userId,
      String accessJti,
      String refreshJti,
      long sessionTtlSeconds,
      long accessTtlSeconds) {
    delegate.openSession(
        sid, tenantId, userId, accessJti, refreshJti, sessionTtlSeconds, accessTtlSeconds);
  }

  @Override
  public RotateResult rotateForRefresh(
      String sid,
      String presentedRefreshJti,
      String newRefreshJti,
      String newAccessJti,
      long sessionTtlSeconds,
      long accessTtlSeconds) {
    SessionStateStore.RotateResult result =
        delegate.rotateForRefresh(
            sid,
            presentedRefreshJti,
            newRefreshJti,
            newAccessJti,
            sessionTtlSeconds,
            accessTtlSeconds);
    return new RotateResult(
        result.success(),
        result.sessionInactive(),
        result.refreshReused(),
        result.previousAccessJti());
  }

  @Override
  public boolean isSessionActive(String sid) {
    return delegate.isSessionActive(sid);
  }

  @Override
  public boolean isAccessTokenActive(String sid, String accessJti) {
    return delegate.isAccessTokenActive(sid, accessJti);
  }

  @Override
  public void revokeSession(String sid) {
    delegate.revokeSession(sid);
  }

  @Override
  public boolean replaceAccessToken(
      String sid, String expectedAccessJti, String newAccessJti, long accessTtlSeconds) {
    return delegate.replaceAccessToken(sid, expectedAccessJti, newAccessJti, accessTtlSeconds);
  }

  @Override
  public void revokeAccessToken(String accessJti) {
    delegate.revokeAccessToken(accessJti);
  }
}
