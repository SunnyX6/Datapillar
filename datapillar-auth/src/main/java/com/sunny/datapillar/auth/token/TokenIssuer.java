package com.sunny.datapillar.auth.token;

/** Low-level token issuer contract. */
public interface TokenIssuer {

  String issue(TokenClaims claims);
}
