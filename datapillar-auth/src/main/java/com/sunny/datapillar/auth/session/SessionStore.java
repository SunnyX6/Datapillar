package com.sunny.datapillar.auth.session;

/** Session storage abstraction. */
public interface SessionStore {

  void openSession(
      String sid,
      Long tenantId,
      Long userId,
      String accessJti,
      String refreshJti,
      long sessionTtlSeconds,
      long accessTtlSeconds);

  RotateResult rotateForRefresh(
      String sid,
      String presentedRefreshJti,
      String newRefreshJti,
      String newAccessJti,
      long sessionTtlSeconds,
      long accessTtlSeconds);

  boolean isSessionActive(String sid);

  boolean isAccessTokenActive(String sid, String accessJti);

  void revokeSession(String sid);

  boolean replaceAccessToken(
      String sid, String expectedAccessJti, String newAccessJti, long accessTtlSeconds);

  void revokeAccessToken(String accessJti);

  record RotateResult(
      boolean success, boolean sessionInactive, boolean refreshReused, String previousAccessJti) {}
}
