package com.sunny.datapillar.auth.token;

import io.jsonwebtoken.Claims;

/** Unified token capability entrypoint. */
public interface TokenEngine {

  String issueAccessToken(TokenClaims claims);

  String issueRefreshToken(TokenClaims claims);

  Claims verify(String token);

  long accessTokenTtlSeconds();

  long refreshTokenTtlSeconds(boolean rememberMe);
}
