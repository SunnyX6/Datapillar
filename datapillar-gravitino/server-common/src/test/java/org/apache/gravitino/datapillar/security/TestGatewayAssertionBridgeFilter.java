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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.gravitino.auth.AuthConstants;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.datapillar.context.TenantContextHolder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestGatewayAssertionBridgeFilter {
  private static final String ASSERTION_HEADER = "X-Gateway-Assertion";
  private static final String EXPECTED_ISSUER = "datapillar-auth";
  private static final String EXPECTED_AUDIENCE = "datapillar-gravitino";

  @TempDir Path tempDir;

  @Test
  public void testDoFilterInjectsAuthorizationAndTenantContext() throws Exception {
    RSAKey keyPair = new RSAKeyGenerator(2048).generate();
    GatewayAssertionBridgeFilter filter = createFilter(keyPair);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    String requestPath = "/api/onemeta/metalakes/OneMeta/catalogs";
    String token =
        buildAssertionToken(
            keyPair, "10086", 2001L, "tenant-dev", "Tenant Dev", "GET", requestPath);

    when(request.getHeader(ASSERTION_HEADER)).thenReturn(token);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn(requestPath);
    when(request.getHeaderNames())
        .thenReturn(Collections.enumeration(Collections.singletonList(ASSERTION_HEADER)));

    doAnswer(
            invocation -> {
              HttpServletRequest wrappedRequest = (HttpServletRequest) invocation.getArgument(0);
              String authHeader = wrappedRequest.getHeader(AuthConstants.HTTP_HEADER_AUTHORIZATION);
              Assertions.assertTrue(
                  authHeader.startsWith(AuthConstants.AUTHORIZATION_BASIC_HEADER));

              String encodedCredential =
                  authHeader.substring(AuthConstants.AUTHORIZATION_BASIC_HEADER.length());
              String decodedCredential =
                  new String(Base64.getDecoder().decode(encodedCredential), StandardCharsets.UTF_8);
              Assertions.assertEquals("uid:2001:10086:gateway_assertion", decodedCredential);

              TenantContext tenantContext = TenantContextHolder.get();
              Assertions.assertNotNull(tenantContext);
              Assertions.assertEquals(2001L, tenantContext.tenantId());
              Assertions.assertEquals("tenant-dev", tenantContext.tenantCode());
              Assertions.assertEquals("Tenant Dev", tenantContext.tenantName());

              Set<String> headerNames = toSet(wrappedRequest.getHeaderNames());
              Assertions.assertTrue(headerNames.contains(AuthConstants.HTTP_HEADER_AUTHORIZATION));
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    filter.doFilter(request, response, chain);
    verify(response, never()).sendError(anyInt(), anyString());
    Assertions.assertNull(TenantContextHolder.get());
  }

  @Test
  public void testDoFilterRejectsRequestWithoutAssertion() throws Exception {
    RSAKey keyPair = new RSAKeyGenerator(2048).generate();
    GatewayAssertionBridgeFilter filter = createFilter(keyPair);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getHeader(ASSERTION_HEADER)).thenReturn(null);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/onemeta/metalakes/OneMeta/catalogs");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    verify(response)
        .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing gateway assertion token");
    Assertions.assertNull(TenantContextHolder.get());
  }

  @Test
  public void testDoFilterRejectsMethodMismatch() throws Exception {
    RSAKey keyPair = new RSAKeyGenerator(2048).generate();
    GatewayAssertionBridgeFilter filter = createFilter(keyPair);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    String requestPath = "/api/onemeta/metalakes/OneMeta/catalogs";
    String token =
        buildAssertionToken(
            keyPair, "10086", 2001L, "tenant-dev", "Tenant Dev", "POST", requestPath);

    when(request.getHeader(ASSERTION_HEADER)).thenReturn(token);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn(requestPath);
    when(request.getHeaderNames())
        .thenReturn(Collections.enumeration(Collections.singletonList(ASSERTION_HEADER)));

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    verify(response)
        .sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), contains("method mismatch"));
    Assertions.assertNull(TenantContextHolder.get());
  }

  @Test
  public void testInitBuildsVerifierFromFilterConfig() throws Exception {
    RSAKey keyPair = new RSAKeyGenerator(2048).generate();
    GatewayAssertionBridgeFilter filter = createFilter(keyPair);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    String requestPath = "/api/onemeta/metalakes/OneMeta/catalogs";
    String token =
        buildAssertionToken(
            keyPair, "10086", 2001L, "tenant-dev", "Tenant Dev", "GET", requestPath);

    when(request.getHeader(ASSERTION_HEADER)).thenReturn(token);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn(requestPath);
    when(request.getHeaderNames())
        .thenReturn(Collections.enumeration(Collections.singletonList(ASSERTION_HEADER)));

    filter.doFilter(request, response, chain);
    verify(chain).doFilter(any(), any());
    verify(response, never()).sendError(anyInt(), anyString());
  }

  private GatewayAssertionBridgeFilter createFilter(RSAKey keyPair) throws Exception {
    Path publicKeyPath = tempDir.resolve("gateway-public.pem");
    Files.write(
        publicKeyPath,
        toPem("PUBLIC KEY", keyPair.toRSAPublicKey().getEncoded())
            .getBytes(StandardCharsets.US_ASCII));

    Map<String, String> initParams = new HashMap<>();
    initParams.put(GatewayAssertionVerifier.INIT_PARAM_ISSUER, EXPECTED_ISSUER);
    initParams.put(GatewayAssertionVerifier.INIT_PARAM_AUDIENCE, EXPECTED_AUDIENCE);
    initParams.put(GatewayAssertionVerifier.INIT_PARAM_PUBLIC_KEY_PATH, publicKeyPath.toString());
    initParams.put(GatewayAssertionBridgeFilter.INIT_PARAM_ASSERTION_HEADER, ASSERTION_HEADER);

    GatewayAssertionBridgeFilter filter = new GatewayAssertionBridgeFilter();
    filter.init(new SimpleFilterConfig(initParams));
    return filter;
  }

  private String buildAssertionToken(
      RSAKey keyPair,
      String userId,
      long tenantId,
      String tenantCode,
      String tenantName,
      String method,
      String path)
      throws JOSEException {
    Instant now = Instant.now();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(EXPECTED_ISSUER)
            .subject(userId)
            .audience(Collections.singletonList(EXPECTED_AUDIENCE))
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(60)))
            .claim(GatewayAssertionClaims.TENANT_ID, tenantId)
            .claim(GatewayAssertionClaims.TENANT_CODE, tenantCode)
            .claim(GatewayAssertionClaims.TENANT_NAME, tenantName)
            .claim(GatewayAssertionClaims.METHOD, method)
            .claim(GatewayAssertionClaims.PATH, path)
            .build();

    SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
    JWSSigner signer = new RSASSASigner(keyPair.toPrivateKey());
    signedJWT.sign(signer);
    return signedJWT.serialize();
  }

  private String toPem(String type, byte[] derBytes) {
    String base64 = Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(derBytes);
    return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
  }

  private Set<String> toSet(Enumeration<String> values) {
    Set<String> result = new LinkedHashSet<>();
    while (values.hasMoreElements()) {
      result.add(values.nextElement());
    }
    return result;
  }

  private static class SimpleFilterConfig implements FilterConfig {
    private final Map<String, String> params;

    private SimpleFilterConfig(Map<String, String> params) {
      this.params = params;
    }

    @Override
    public String getFilterName() {
      return "SimpleFilterConfig";
    }

    @Override
    public String getInitParameter(String name) {
      return params.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return Collections.enumeration(params.keySet());
    }

    @Override
    public ServletContext getServletContext() {
      return null;
    }
  }
}
