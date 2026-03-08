package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.security.Ed25519JwkSupport;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Provider that loads and caches issuer JWKS documents from auth. */
@Slf4j
@Component
public class IssuerJwksProvider {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final AuthenticationProperties properties;
  private final WebClient webClient;

  private volatile CachedJwks cachedJwks = new CachedJwks(Map.of(), 0L);
  private volatile Mono<CachedJwks> inFlightRefresh;

  public IssuerJwksProvider(
      AuthenticationProperties properties, WebClient.Builder webClientBuilder) {
    this.properties = properties;
    this.webClient = webClientBuilder.build();
    this.properties.validate();
    this.cachedJwks = refreshBlocking();
  }

  public Mono<PublicKey> resolve(String kid, String traceId) {
    if (kid == null || kid.isBlank()) {
      return Mono.error(new GatewayUnauthorizedException("Invalid token"));
    }
    return loadSnapshot(kid, traceId)
        .flatMap(
            snapshot -> {
              PublicKey publicKey = snapshot.keysByKid().get(kid);
              if (publicKey == null) {
                return Mono.error(new GatewayUnauthorizedException("Unknown token key"));
              }
              return Mono.just(publicKey);
            });
  }

  private Mono<CachedJwks> loadSnapshot(String kid, String traceId) {
    CachedJwks snapshot = cachedJwks;
    long now = System.currentTimeMillis();
    if (now < snapshot.expiresAtMillis() && snapshot.keysByKid().containsKey(kid)) {
      return Mono.just(snapshot);
    }
    return refresh(traceId);
  }

  private Mono<CachedJwks> refresh(String traceId) {
    Mono<CachedJwks> running = inFlightRefresh;
    if (running != null) {
      return running;
    }

    synchronized (this) {
      if (inFlightRefresh == null) {
        inFlightRefresh =
            fetchJwks(traceId)
                .doOnNext(snapshot -> cachedJwks = snapshot)
                .doFinally(signalType -> inFlightRefresh = null)
                .cache();
      }
      return inFlightRefresh;
    }
  }

  private CachedJwks refreshBlocking() {
    try {
      CachedJwks snapshot = fetchJwks(null).block(REQUEST_TIMEOUT);
      if (snapshot == null) {
        throw new IllegalStateException("issuer JWKS response is empty");
      }
      return snapshot;
    } catch (Throwable ex) {
      throw new IllegalStateException("Failed to load auth JWKS from issuer", ex);
    }
  }

  private Mono<CachedJwks> fetchJwks(String traceId) {
    return webClient
        .get()
        .uri(properties.issuerJwksUri())
        .headers(
            headers -> {
              headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
              if (StringUtils.hasText(traceId)) {
                headers.set(HeaderConstants.HEADER_TRACE_ID, traceId.trim());
              }
            })
        .retrieve()
        .bodyToMono(MAP_TYPE)
        .timeout(REQUEST_TIMEOUT)
        .map(this::parseJwks)
        .onErrorMap(
            throwable -> {
              log.error(
                  "security_event event=jwks_fetch_failed issuer={} reason={}",
                  properties.getIssuer(),
                  throwable.getMessage(),
                  throwable);
              return new GatewayServiceUnavailableException(
                  throwable, "Failed to load issuer JWKS");
            });
  }

  private CachedJwks parseJwks(Map<String, Object> payload) {
    if (payload == null) {
      throw new IllegalStateException("issuer JWKS response is empty");
    }
    Object rawKeys = payload.get("keys");
    if (!(rawKeys instanceof List<?> keys) || keys.isEmpty()) {
      throw new IllegalStateException("issuer JWKS response missing keys");
    }

    Map<String, PublicKey> resolved = new LinkedHashMap<>();
    for (Object rawKey : keys) {
      if (!(rawKey instanceof Map<?, ?> keyMap)) {
        throw new IllegalStateException("issuer JWKS contains invalid key entry");
      }
      Map<String, Object> normalized = normalize(keyMap);
      validateJwk(normalized);
      String kid = String.valueOf(normalized.get("kid")).trim();
      resolved.put(kid, Ed25519JwkSupport.parseEd25519PublicKeyFromJwk(normalized));
    }

    if (resolved.isEmpty()) {
      throw new IllegalStateException("issuer JWKS response contains no usable keys");
    }

    long expiresAtMillis = System.currentTimeMillis() + properties.getJwksCacheSeconds() * 1000L;
    return new CachedJwks(Map.copyOf(resolved), expiresAtMillis);
  }

  private Map<String, Object> normalize(Map<?, ?> rawKey) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawKey.entrySet()) {
      if (entry.getKey() != null) {
        normalized.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    return normalized;
  }

  private void validateJwk(Map<String, Object> jwk) {
    if (!"EdDSA".equals(jwk.get("alg"))) {
      throw new IllegalStateException("issuer JWKS key alg must be EdDSA");
    }
    if (!"sig".equals(jwk.get("use"))) {
      throw new IllegalStateException("issuer JWKS key use must be sig");
    }
    Object kid = jwk.get("kid");
    if (!(kid instanceof String text) || text.trim().isEmpty()) {
      throw new IllegalStateException("issuer JWKS key kid cannot be empty");
    }
  }

  private record CachedJwks(Map<String, PublicKey> keysByKid, long expiresAtMillis) {}
}
