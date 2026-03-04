package com.sunny.datapillar.auth.token;

import io.jsonwebtoken.Claims;

/** Low-level token verifier contract. */
public interface TokenVerifier {

  Claims verify(String token);
}
