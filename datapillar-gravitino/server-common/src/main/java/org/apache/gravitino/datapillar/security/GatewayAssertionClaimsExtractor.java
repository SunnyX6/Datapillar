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

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.exceptions.UnauthorizedException;

/** Extracts tenant-aware context from validated gateway assertion claims. */
public class GatewayAssertionClaimsExtractor {

  public GatewayAssertionContext extract(
      JWTClaimsSet claims, String requestMethod, String requestPath) {
    if (claims == null) {
      throw new UnauthorizedException("Gateway assertion claims are required");
    }

    String userId = StringUtils.trimToNull(claims.getSubject());
    if (userId == null) {
      throw new UnauthorizedException("Gateway assertion subject is required");
    }

    long tenantId = extractTenantId(claims.getClaim(GatewayAssertionClaims.TENANT_ID));
    String tenantCode =
        StringUtils.trimToNull(stringClaim(claims, GatewayAssertionClaims.TENANT_CODE));
    if (tenantCode == null) {
      throw new UnauthorizedException("Gateway assertion claim tenantCode is required");
    }
    String tenantName =
        StringUtils.trimToNull(stringClaim(claims, GatewayAssertionClaims.TENANT_NAME));

    validateMethod(claims, requestMethod);
    validatePath(claims, requestPath);

    TenantContext tenantContext =
        TenantContext.builder()
            .withTenantId(tenantId)
            .withTenantCode(tenantCode)
            .withTenantName(tenantName)
            .build();
    String username = StringUtils.trimToNull(stringClaim(claims, GatewayAssertionClaims.USERNAME));
    return new GatewayAssertionContext(userId, username, tenantContext);
  }

  private String stringClaim(JWTClaimsSet claims, String claimKey) {
    Object value = claims.getClaim(claimKey);
    return value == null ? null : String.valueOf(value);
  }

  private long extractTenantId(Object rawTenantId) {
    if (rawTenantId == null) {
      throw new UnauthorizedException("Gateway assertion claim tenantId is required");
    }

    if (rawTenantId instanceof Number) {
      long tenantId = ((Number) rawTenantId).longValue();
      if (tenantId <= 0) {
        throw new UnauthorizedException("Gateway assertion tenantId must be positive");
      }
      return tenantId;
    }

    if (rawTenantId instanceof String) {
      try {
        long tenantId = Long.parseLong(((String) rawTenantId).trim());
        if (tenantId <= 0) {
          throw new UnauthorizedException("Gateway assertion tenantId must be positive");
        }
        return tenantId;
      } catch (NumberFormatException e) {
        throw new UnauthorizedException(
            "Gateway assertion tenantId is not a valid number: %s", rawTenantId);
      }
    }

    throw new UnauthorizedException(
        "Gateway assertion tenantId has unsupported type: %s",
        (Object) rawTenantId.getClass().getName());
  }

  private void validateMethod(JWTClaimsSet claims, String requestMethod) {
    String assertionMethod = stringClaim(claims, GatewayAssertionClaims.METHOD);
    if (StringUtils.isBlank(assertionMethod)) {
      throw new UnauthorizedException("Gateway assertion claim method is required");
    }
    if (!normalizeMethod(assertionMethod).equals(normalizeMethod(requestMethod))) {
      throw new UnauthorizedException(
          "Gateway assertion method mismatch, expected %s but was %s",
          normalizeMethod(assertionMethod), normalizeMethod(requestMethod));
    }
  }

  private void validatePath(JWTClaimsSet claims, String requestPath) {
    String assertionPath = stringClaim(claims, GatewayAssertionClaims.PATH);
    if (StringUtils.isBlank(assertionPath)) {
      throw new UnauthorizedException("Gateway assertion claim path is required");
    }
    if (!normalizePath(assertionPath).equals(normalizePath(requestPath))) {
      throw new UnauthorizedException(
          "Gateway assertion path mismatch, expected %s but was %s",
          normalizePath(assertionPath), normalizePath(requestPath));
    }
  }

  static String normalizeMethod(String method) {
    return StringUtils.isBlank(method) ? "" : method.trim().toUpperCase(Locale.ROOT);
  }

  static String normalizePath(String path) {
    if (StringUtils.isBlank(path)) {
      return "/";
    }

    String normalized = path.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }

    if (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
