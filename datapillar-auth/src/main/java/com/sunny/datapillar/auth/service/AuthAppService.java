package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.authentication.Authenticator;
import com.sunny.datapillar.auth.config.AuthProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Auth application service. */
@Service
public class AuthAppService {

  private final Authenticator authenticator;
  private final AuthProperties authProperties;

  public AuthAppService(Authenticator authenticator, AuthProperties authProperties) {
    this.authenticator = authenticator;
    this.authProperties = authProperties;
  }

  public Map<String, Object> health() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "OK");
    payload.put("authenticator", authenticator.name());
    payload.put("issuer", authProperties.getToken().getIssuer());
    payload.put("active_kid", authProperties.getJwks().getActiveKid());
    payload.put("jwks_enabled", authProperties.getJwks().isEnabled());
    return payload;
  }
}
