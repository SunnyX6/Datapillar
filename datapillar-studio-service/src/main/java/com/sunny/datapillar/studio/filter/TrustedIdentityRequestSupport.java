package com.sunny.datapillar.studio.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/** Trusted identity request support utilities. */
final class TrustedIdentityRequestSupport {

  private static final Set<String> WHITELIST_PREFIXES =
      Set.of("/actuator/health", "/actuator/info", "/v3/api-docs", "/setup", "/biz/invitations");

  private TrustedIdentityRequestSupport() {}

  static boolean shouldSkip(HttpServletRequest request, boolean enabled) {
    return !enabled || isWhitelisted(request);
  }

  static String normalizedPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || path.isBlank()) {
      return "/";
    }
    return path;
  }

  private static boolean isWhitelisted(HttpServletRequest request) {
    String path = normalizedPath(request);
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
      if (path.isEmpty()) {
        path = "/";
      }
    }
    for (String prefix : WHITELIST_PREFIXES) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
