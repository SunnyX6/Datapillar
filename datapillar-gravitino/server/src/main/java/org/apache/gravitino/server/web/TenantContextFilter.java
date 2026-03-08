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
package org.apache.gravitino.server.web;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.multitenancy.context.ExternalUserIdContextHolder;
import org.apache.gravitino.multitenancy.context.TenantContext;
import org.apache.gravitino.multitenancy.context.TenantContextHolder;

/** Resolves tenant headers and binds tenant context for one request lifecycle. */
public class TenantContextFilter implements Filter {

  static final String HEADER_TENANT_ID = "X-Tenant-Id";
  static final String HEADER_TENANT_CODE = "X-Tenant-Code";
  static final String HEADER_TENANT_NAME = "X-Tenant-Name";
  static final String HEADER_EXTERNAL_USER_ID = "X-External-User-Id";
  static final String VERSION_PATH = "/api/version";
  static final String MISSING_TENANT_HEADERS_ERROR =
      "Missing required tenant headers: X-Tenant-Id, X-Tenant-Code";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    clearAllContexts();
    if (shouldBypass(httpRequest)) {
      try {
        chain.doFilter(request, response);
      } finally {
        clearAllContexts();
      }
      return;
    }

    String tenantIdValue = trimToNull(httpRequest.getHeader(HEADER_TENANT_ID));
    String tenantCode = trimToNull(httpRequest.getHeader(HEADER_TENANT_CODE));
    if (tenantIdValue == null || tenantCode == null) {
      httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, MISSING_TENANT_HEADERS_ERROR);
      return;
    }

    long tenantId;
    try {
      tenantId = parsePositiveTenantId(tenantIdValue);
    } catch (IllegalArgumentException exception) {
      httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
      return;
    }

    String tenantName = trimToNull(httpRequest.getHeader(HEADER_TENANT_NAME));
    TenantContext context =
        TenantContext.builder()
            .withTenantId(tenantId)
            .withTenantCode(tenantCode)
            .withTenantName(tenantName == null ? tenantCode : tenantName)
            .build();

    String externalUserId = trimToNull(httpRequest.getHeader(HEADER_EXTERNAL_USER_ID));

    TenantContextHolder.set(context);
    if (externalUserId != null) {
      ExternalUserIdContextHolder.set(externalUserId);
    }
    try {
      chain.doFilter(request, response);
    } finally {
      clearAllContexts();
    }
  }

  @Override
  public void destroy() {}

  private boolean shouldBypass(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String requestPath = request.getRequestURI();
    return StringUtils.endsWith(requestPath, VERSION_PATH);
  }

  private long parsePositiveTenantId(String tenantIdValue) {
    try {
      long tenantId = Long.parseLong(tenantIdValue);
      if (tenantId <= 0) {
        throw new IllegalArgumentException("X-Tenant-Id must be a positive number");
      }
      return tenantId;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("X-Tenant-Id must be a valid number", exception);
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private void clearAllContexts() {
    TenantContextHolder.remove();
    ExternalUserIdContextHolder.remove();
  }
}
