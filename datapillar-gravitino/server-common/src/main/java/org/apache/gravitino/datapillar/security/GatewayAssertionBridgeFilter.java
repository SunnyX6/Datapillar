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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.auth.AuthConstants;
import org.apache.gravitino.datapillar.context.TenantContextHolder;
import org.apache.gravitino.exceptions.UnauthorizedException;

/**
 * Validates gateway assertion and bridges it into Gravitino's standard Authorization header.
 *
 * <p>This filter enforces gateway assertion as the only authentication input path.
 */
public class GatewayAssertionBridgeFilter implements Filter {
  static final String INIT_PARAM_ASSERTION_HEADER = "assertionHeader";
  static final String DEFAULT_ASSERTION_HEADER = "X-Gateway-Assertion";
  private static final String BRIDGE_PASSWORD = "gateway_assertion";

  private GatewayAssertionVerifier gatewayAssertionVerifier;
  private String assertionHeaderName = DEFAULT_ASSERTION_HEADER;

  public GatewayAssertionBridgeFilter() {}

  @VisibleForTesting
  GatewayAssertionBridgeFilter(
      GatewayAssertionVerifier gatewayAssertionVerifier, String assertionHeaderName) {
    this.gatewayAssertionVerifier = gatewayAssertionVerifier;
    if (StringUtils.isNotBlank(assertionHeaderName)) {
      this.assertionHeaderName = assertionHeaderName;
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    if (StringUtils.isNotBlank(filterConfig.getInitParameter(INIT_PARAM_ASSERTION_HEADER))) {
      this.assertionHeaderName = filterConfig.getInitParameter(INIT_PARAM_ASSERTION_HEADER).trim();
    }
    if (this.gatewayAssertionVerifier == null) {
      this.gatewayAssertionVerifier = GatewayAssertionVerifier.fromFilterConfig(filterConfig);
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      String assertionToken = httpRequest.getHeader(assertionHeaderName);
      GatewayAssertionContext assertionContext =
          gatewayAssertionVerifier.verify(
              assertionToken, httpRequest.getMethod(), httpRequest.getRequestURI());

      TenantContextHolder.set(assertionContext.tenantContext());
      String principal =
          TenantPrincipalAdapter.toTenantScopedPrincipal(
              assertionContext.tenantContext().tenantId(), assertionContext.userId());

      MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(httpRequest);
      mutableRequest.putHeader(
          AuthConstants.HTTP_HEADER_AUTHORIZATION, buildAuthorizationHeader(principal));
      chain.doFilter(mutableRequest, response);
    } catch (UnauthorizedException e) {
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
    } finally {
      TenantContextHolder.remove();
    }
  }

  @Override
  public void destroy() {}

  private static String buildAuthorizationHeader(String principal) {
    String credential = principal + ":" + BRIDGE_PASSWORD;
    String encodedCredential =
        Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    return AuthConstants.AUTHORIZATION_BASIC_HEADER + encodedCredential;
  }

  /** Mutable request wrapper used to bridge assertion into Authorization header. */
  static final class MutableHttpServletRequest extends HttpServletRequestWrapper {
    private final Map<String, String> customHeaders;

    MutableHttpServletRequest(HttpServletRequest request) {
      super(request);
      this.customHeaders = new HashMap<>();
    }

    void putHeader(String name, String value) {
      customHeaders.put(name, value);
    }

    @Override
    public String getHeader(String name) {
      String customizedValue = customHeaders.get(name);
      if (customizedValue != null) {
        return customizedValue;
      }
      return ((HttpServletRequest) getRequest()).getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (customHeaders.containsKey(name)) {
        return Collections.enumeration(Collections.singletonList(customHeaders.get(name)));
      }
      return ((HttpServletRequest) getRequest()).getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> mergedHeaderNames = new LinkedHashSet<>(customHeaders.keySet());
      Enumeration<String> headerNames = ((HttpServletRequest) getRequest()).getHeaderNames();
      while (headerNames.hasMoreElements()) {
        mergedHeaderNames.add(headerNames.nextElement());
      }
      List<String> headerNameList = new ArrayList<>(mergedHeaderNames);
      return Collections.enumeration(headerNameList);
    }
  }
}
