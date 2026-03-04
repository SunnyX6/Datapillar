package com.sunny.datapillar.auth.service.login.method.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Store for SSO state lifecycle management.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SsoStateStore {

  private static final String PAYLOAD_KEY_PREFIX = "sso:state:payload:";
  private static final String ISSUED_KEY_PREFIX = "sso:state:issued:";
  private static final String CONSUMED_KEY_PREFIX = "sso:state:consumed:";

  private static final String RESULT_INVALID = "__INVALID__";
  private static final String RESULT_EXPIRED = "__EXPIRED__";
  private static final String RESULT_REPLAYED = "__REPLAYED__";

  private static final int MIN_STATE_BYTES = 16;

  private static final String CONSUME_SCRIPT =
      "local payload=redis.call('GET', KEYS[1]); "
          + "if payload then redis.call('DEL', KEYS[1]); redis.call('SETEX', KEYS[2], ARGV[1], '1'); return payload; end; "
          + "if redis.call('EXISTS', KEYS[2]) == 1 then return '"
          + RESULT_REPLAYED
          + "'; end; "
          + "if redis.call('EXISTS', KEYS[3]) == 1 then return '"
          + RESULT_EXPIRED
          + "'; end; "
          + "return '"
          + RESULT_INVALID
          + "';";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Base64.Encoder STATE_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final DefaultRedisScript<String> consumeScript;

  @Value("${sso.state-ttl-seconds:300}")
  private long stateTtlSeconds;

  @Value("${sso.state-replay-ttl-seconds:3600}")
  private long replayStateTtlSeconds;

  @Value("${sso.state-bytes:24}")
  private int stateBytes;

  public SsoStateStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.objectMapper = objectMapper;
    this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
  }

  @PostConstruct
  public void validateStateBytesConfig() {
    if (stateBytes < MIN_STATE_BYTES) {
      throw new IllegalStateException("sso.state-bytes must be >= " + MIN_STATE_BYTES);
    }
  }

  public String createState(Long tenantId, String provider) {
    return createState(tenantId, provider, null, null, null);
  }

  public String createState(
      Long tenantId,
      String provider,
      String nonce,
      String codeChallenge,
      String codeChallengeMethod) {
    validateStateBytesConfig();
    String normalizedProvider = normalize(provider);
    String normalizedNonce = normalize(nonce);
    String normalizedCodeChallenge = normalize(codeChallenge);
    String normalizedCodeChallengeMethod = normalizeCodeChallengeMethod(codeChallengeMethod);

    if (normalizedNonce == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "nonce must not be blank");
    }
    if (normalizedCodeChallenge == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "code_challenge must not be blank");
    }

    long issuedTtlSeconds = Math.max(replayStateTtlSeconds, stateTtlSeconds + 60);
    for (int i = 0; i < 5; i++) {
      String state = generateState();
      String payloadKey = buildPayloadKey(state);
      String issuedKey = buildIssuedKey(state);
      StatePayload payload =
          new StatePayload(
              tenantId,
              normalizedProvider,
              normalizedNonce,
              normalizedCodeChallenge,
              normalizedCodeChallengeMethod);
      try {
        String value = objectMapper.writeValueAsString(payload);
        Boolean created =
            stringRedisTemplate
                .opsForValue()
                .setIfAbsent(payloadKey, value, Duration.ofSeconds(stateTtlSeconds));
        if (Boolean.TRUE.equals(created)) {
          stringRedisTemplate
              .opsForValue()
              .set(issuedKey, "1", Duration.ofSeconds(issuedTtlSeconds));
          return state;
        }
      } catch (Throwable ex) {
        throw new com.sunny.datapillar.common.exception.InternalException(
            ex, "Failed to generate SSO state");
      }
    }
    throw new com.sunny.datapillar.common.exception.InternalException(
        "Failed to generate SSO state");
  }

  public StatePayload consumeOrThrow(String state, Long expectedTenantId, String expectedProvider) {
    return consumeOrThrow(state, expectedTenantId, expectedProvider, null, null);
  }

  public StatePayload consumeOrThrow(
      String state,
      Long expectedTenantId,
      String expectedProvider,
      String expectedNonce,
      String codeVerifier) {
    if (state == null || state.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid SSO state");
    }
    String result =
        stringRedisTemplate.execute(
            consumeScript,
            List.of(buildPayloadKey(state), buildConsumedKey(state), buildIssuedKey(state)),
            String.valueOf(Math.max(replayStateTtlSeconds, 1L)));

    if (result == null || RESULT_INVALID.equals(result)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid SSO state");
    }
    if (RESULT_EXPIRED.equals(result)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "SSO state has expired");
    }
    if (RESULT_REPLAYED.equals(result)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "SSO state has already been used");
    }

    StatePayload payload;
    try {
      payload = objectMapper.readValue(result, StatePayload.class);
    } catch (Throwable ex) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          ex, "Invalid SSO state");
    }

    if (expectedTenantId != null
        && (payload.getTenantId() == null || !expectedTenantId.equals(payload.getTenantId()))) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid SSO state");
    }
    String normalizedExpectedProvider = normalize(expectedProvider);
    if (normalizedExpectedProvider != null
        && !normalizedExpectedProvider.equals(payload.getProvider())) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid SSO state");
    }

    String normalizedExpectedNonce = normalize(expectedNonce);
    if (normalizedExpectedNonce == null
        || payload.getNonce() == null
        || !normalizedExpectedNonce.equals(payload.getNonce())) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid SSO nonce");
    }
    validatePkce(payload, codeVerifier);

    return payload;
  }

  private void validatePkce(StatePayload payload, String codeVerifier) {
    String normalizedCodeVerifier = normalize(codeVerifier);
    if (normalizedCodeVerifier == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Invalid SSO code_verifier");
    }
    String codeChallengeMethod =
        payload.getCodeChallengeMethod() == null ? "s256" : payload.getCodeChallengeMethod();
    String expectedChallenge = payload.getCodeChallenge();
    if (expectedChallenge == null || expectedChallenge.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Invalid SSO code_challenge");
    }

    if ("plain".equals(codeChallengeMethod)) {
      if (!expectedChallenge.equals(normalizedCodeVerifier)) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "SSO PKCE validation failed");
      }
      return;
    }

    if (!"s256".equals(codeChallengeMethod)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Invalid SSO code_challenge_method");
    }

    String actualChallenge = toS256Challenge(normalizedCodeVerifier);
    if (!expectedChallenge.equals(actualChallenge)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "SSO PKCE validation failed");
    }
  }

  private String toS256Challenge(String codeVerifier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Throwable ex) {
      throw new com.sunny.datapillar.common.exception.InternalException(
          ex, "Failed to compute SSO PKCE");
    }
  }

  private String generateState() {
    byte[] randomBytes = new byte[stateBytes];
    SECURE_RANDOM.nextBytes(randomBytes);
    return STATE_ENCODER.encodeToString(randomBytes);
  }

  private String buildPayloadKey(String state) {
    return PAYLOAD_KEY_PREFIX + state;
  }

  private String buildIssuedKey(String state) {
    return ISSUED_KEY_PREFIX + state;
  }

  private String buildConsumedKey(String state) {
    return CONSUMED_KEY_PREFIX + state;
  }

  private String normalize(String provider) {
    if (provider == null || provider.isBlank()) {
      return null;
    }
    return provider.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeCodeChallengeMethod(String value) {
    if (value == null || value.isBlank()) {
      return "s256";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  @Data
  @AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class StatePayload {
    private Long tenantId;
    private String provider;
    private String nonce;
    private String codeChallenge;
    private String codeChallengeMethod;
  }
}
