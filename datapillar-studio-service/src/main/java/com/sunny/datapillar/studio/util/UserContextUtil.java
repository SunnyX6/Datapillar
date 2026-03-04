package com.sunny.datapillar.studio.util;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * User context tool class Provide user context common tool capabilities
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class UserContextUtil {

  private UserContextUtil() {}

  /** Get current request */
  private static HttpServletRequest getRequest() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletAttributes) {
      return servletAttributes.getRequest();
    }
    return null;
  }

  /** Get current user ID */
  public static Long getUserId() {
    TrustedIdentityContext context = getAssertionContext();
    return context == null ? null : context.userId();
  }

  /** Get current user ID（must exist） */
  public static Long getRequiredUserId() {
    Long userId = getUserId();
    if (userId == null) {
      throw new IllegalStateException("The user is not logged in or the user information is lost");
    }
    return userId;
  }

  /** Get current username */
  public static String getUsername() {
    TrustedIdentityContext context = getAssertionContext();
    return context == null ? null : context.username();
  }

  /** Get current tenantID */
  public static Long getTenantId() {
    TrustedIdentityContext context = getAssertionContext();
    return context == null ? null : context.tenantId();
  }

  /** Whether to authorize access for platform super management */
  public static boolean isImpersonation() {
    TrustedIdentityContext context = getAssertionContext();
    return context != null && context.impersonation();
  }

  /** Get the current users email */
  public static String getEmail() {
    TrustedIdentityContext context = getAssertionContext();
    if (context == null) {
      return null;
    }
    String email = context.email();
    return (email == null || email.isEmpty()) ? null : email;
  }

  /** Get link tracking ID */
  public static String getTraceId() {
    HttpServletRequest request = getRequest();
    if (request == null) {
      return null;
    }
    return request.getHeader(HeaderConstants.HEADER_TRACE_ID);
  }

  /** Determine whether you are currently logged in */
  public static boolean isLoggedIn() {
    return getUserId() != null;
  }

  private static TrustedIdentityContext getAssertionContext() {
    HttpServletRequest request = getRequest();
    if (request == null) {
      return null;
    }
    return TrustedIdentityContext.current(request);
  }
}
