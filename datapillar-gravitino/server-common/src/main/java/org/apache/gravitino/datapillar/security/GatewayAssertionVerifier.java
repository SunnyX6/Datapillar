/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.datapillar.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.exceptions.UnauthorizedException;

/** Verifies gateway assertions and extracts tenant-aware request context. */
public class GatewayAssertionVerifier {
  static final String INIT_PARAM_ISSUER = "issuer";
  static final String INIT_PARAM_AUDIENCE = "audience";
  static final String INIT_PARAM_PUBLIC_KEY_PATH = "publicKeyPath";
  static final String INIT_PARAM_CLOCK_SKEW_SECONDS = "clockSkewSeconds";
  static final long DEFAULT_CLOCK_SKEW_SECONDS = 30L;

  private final String expectedIssuer;
  private final String expectedAudience;
  private final long allowSkewSeconds;
  private final JWSVerifier jwsVerifier;
  private final GatewayAssertionClaimsExtractor claimsExtractor;

  GatewayAssertionVerifier(
      String expectedIssuer,
      String expectedAudience,
      long allowSkewSeconds,
      JWSVerifier jwsVerifier,
      GatewayAssertionClaimsExtractor claimsExtractor) {
    this.expectedIssuer = expectedIssuer;
    this.expectedAudience = expectedAudience;
    this.allowSkewSeconds = allowSkewSeconds;
    this.jwsVerifier = jwsVerifier;
    this.claimsExtractor = claimsExtractor;
  }

  public static GatewayAssertionVerifier fromFilterConfig(FilterConfig filterConfig)
      throws ServletException {
    String issuer = requiredParam(filterConfig, INIT_PARAM_ISSUER);
    String audience = requiredParam(filterConfig, INIT_PARAM_AUDIENCE);
    String publicKeyPath = requiredParam(filterConfig, INIT_PARAM_PUBLIC_KEY_PATH);
    long skewSeconds =
        parseClockSkewSeconds(filterConfig.getInitParameter(INIT_PARAM_CLOCK_SKEW_SECONDS));

    try {
      JWSVerifier verifier = createJwsVerifier(publicKeyPath);
      return new GatewayAssertionVerifier(
          issuer, audience, skewSeconds, verifier, new GatewayAssertionClaimsExtractor());
    } catch (IOException e) {
      throw new ServletException(
          "Failed to read gateway assertion public key from path: " + publicKeyPath, e);
    } catch (Exception e) {
      throw new ServletException("Failed to initialize gateway assertion verifier", e);
    }
  }

  public GatewayAssertionContext verify(
      String assertionToken, String requestMethod, String requestPath) {
    if (StringUtils.isBlank(assertionToken)) {
      throw new UnauthorizedException("Missing gateway assertion token");
    }

    try {
      SignedJWT signedJWT = SignedJWT.parse(assertionToken.trim());
      if (!signedJWT.verify(jwsVerifier)) {
        throw new UnauthorizedException("Gateway assertion signature validation failed");
      }

      JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
      validateIssuer(claimsSet.getIssuer());
      validateAudience(claimsSet.getAudience());
      validateTimeWindow(claimsSet);
      return claimsExtractor.extract(claimsSet, requestMethod, requestPath);
    } catch (UnauthorizedException e) {
      throw e;
    } catch (ParseException e) {
      throw new UnauthorizedException(e, "Gateway assertion parsing failed");
    } catch (JOSEException e) {
      throw new UnauthorizedException(e, "Gateway assertion signature validation failed");
    } catch (Exception e) {
      throw new UnauthorizedException(e, "Gateway assertion validation failed");
    }
  }

  private void validateIssuer(String actualIssuer) {
    if (!expectedIssuer.equals(actualIssuer)) {
      throw new UnauthorizedException(
          "Gateway assertion issuer mismatch, expected %s but was %s",
          expectedIssuer, actualIssuer);
    }
  }

  private void validateAudience(List<String> audienceValues) {
    if (audienceValues == null || audienceValues.isEmpty()) {
      throw new UnauthorizedException("Gateway assertion audience is missing");
    }

    for (String audience : audienceValues) {
      if (expectedAudience.equals(audience)) {
        return;
      }
    }

    throw new UnauthorizedException(
        "Gateway assertion audiences %s do not contain %s", audienceValues, expectedAudience);
  }

  private void validateTimeWindow(JWTClaimsSet claimsSet) {
    long skewMillis = allowSkewSeconds * 1000L;
    long nowMillis = System.currentTimeMillis();

    Instant expirationTime = instantOrNull(claimsSet.getExpirationTime());
    if (expirationTime == null) {
      throw new UnauthorizedException("Gateway assertion expiration is missing");
    }
    if (nowMillis - skewMillis > expirationTime.toEpochMilli()) {
      throw new UnauthorizedException("Gateway assertion token is expired");
    }

    Instant notBefore = instantOrNull(claimsSet.getNotBeforeTime());
    if (notBefore != null && nowMillis + skewMillis < notBefore.toEpochMilli()) {
      throw new UnauthorizedException("Gateway assertion token is not active yet");
    }

    Instant issuedAt = instantOrNull(claimsSet.getIssueTime());
    if (issuedAt != null && nowMillis + skewMillis < issuedAt.toEpochMilli()) {
      throw new UnauthorizedException("Gateway assertion issue time is in the future");
    }
  }

  @SuppressWarnings("JavaUtilDate")
  private Instant instantOrNull(java.util.Date date) {
    return date == null ? null : date.toInstant();
  }

  private static JWSVerifier createJwsVerifier(String publicKeyPath) throws Exception {
    String keyContent =
        new String(Files.readAllBytes(Paths.get(publicKeyPath)), StandardCharsets.US_ASCII);
    JWK jwk = JWK.parseFromPEMEncodedObjects(keyContent).toPublicJWK();

    if (jwk instanceof OctetKeyPair) {
      return new Ed25519Verifier((OctetKeyPair) jwk);
    }
    if (jwk instanceof RSAKey) {
      return new RSASSAVerifier(((RSAKey) jwk).toRSAPublicKey());
    }
    if (jwk instanceof ECKey) {
      return new ECDSAVerifier(((ECKey) jwk).toECPublicKey());
    }

    throw new IllegalArgumentException(
        String.format(
            "Unsupported key type for gateway assertion verification: %s",
            jwk.getClass().getName()));
  }

  private static String requiredParam(FilterConfig filterConfig, String key)
      throws ServletException {
    String value = filterConfig.getInitParameter(key);
    if (StringUtils.isBlank(value)) {
      throw new ServletException("Missing required init parameter: " + key);
    }
    return value.trim();
  }

  private static long parseClockSkewSeconds(String configuredSkew) throws ServletException {
    if (StringUtils.isBlank(configuredSkew)) {
      return DEFAULT_CLOCK_SKEW_SECONDS;
    }

    try {
      long skewSeconds = Long.parseLong(configuredSkew.trim());
      if (skewSeconds < 0) {
        throw new ServletException("clockSkewSeconds must be greater than or equal to 0");
      }
      return skewSeconds;
    } catch (NumberFormatException e) {
      throw new ServletException("clockSkewSeconds is not a valid integer", e);
    }
  }
}
