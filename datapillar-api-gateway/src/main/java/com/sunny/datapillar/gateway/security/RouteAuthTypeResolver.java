package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.security.AuthType;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import org.springframework.stereotype.Component;

/** Resolves fixed route-domain authentication type bindings. */
@Component
public class RouteAuthTypeResolver {

  private final AuthenticationProperties properties;

  public RouteAuthTypeResolver(AuthenticationProperties properties) {
    this.properties = properties;
  }

  public boolean isPublicPath(String path) {
    for (String prefix : properties.getPublicPathPrefixes()) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  public AuthType resolve(String path) {
    for (String prefix : properties.getProtectedPathPrefixes()) {
      if (path.startsWith(prefix)) {
        return prefix.startsWith("/openapi/") ? AuthType.API_KEY : AuthType.JWT;
      }
    }
    return null;
  }
}
