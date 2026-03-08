package com.sunny.datapillar.gateway.security;

import reactor.core.publisher.Mono;

/** Strict verifier for gateway access tokens. */
public interface AccessTokenVerifier {

  Mono<VerifiedAccessToken> verify(String token, String traceId);
}
