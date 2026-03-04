package com.sunny.datapillar.studio.security;

import com.sunny.datapillar.studio.util.UserContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Security tools Provide security common tool capabilities
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@Slf4j
public class SecurityUtil {

  /**
   * Get the username of the currently authenticated user
   *
   * @return Username，Return if not authenticatednull
   */
  public String getCurrentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !"anonymousUser".equals(authentication.getName())) {
      return authentication.getName();
    }
    return null;
  }

  /**
   * Get the user of the current authenticated userID
   *
   * @return UserID，Throws an exception if not authenticated or the user does not exist
   */
  public Long getCurrentUserId() {
    return UserContextUtil.getUserId();
  }

  /**
   * Get current authenticated user information
   *
   * @return User information，Throws an exception if not authenticated or the user does not exist
   */
  public String getCurrentUser() {
    return UserContextUtil.getUsername();
  }

  /**
   * Check if the current user is authenticated
   *
   * @return Has it been certified?
   */
  public boolean isAuthenticated() {
    return getCurrentUsername() != null;
  }

  /**
   * Check if the current user is an administrator
   *
   * @return Is it an administrator?
   */
  public boolean isCurrentUserAdmin() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null || authentication.getAuthorities() == null) {
        return false;
      }
      return authentication.getAuthorities().stream()
          .map(GrantedAuthority::getAuthority)
          .anyMatch("ADMIN"::equalsIgnoreCase);
    } catch (Throwable e) {
      log.warn("Exception occurred while checking user permissions", e);
      return false;
    }
  }
}
