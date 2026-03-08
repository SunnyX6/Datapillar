package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.authentication.Authenticator;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.key.KeyManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Auth application service. */
@Service
public class AuthAppService {

  private final Authenticator authenticator;
  private final AuthProperties authProperties;
  private final KeyManager keyManager;

  public AuthAppService(
      Authenticator authenticator, AuthProperties authProperties, KeyManager keyManager) {
    this.authenticator = authenticator;
    this.authProperties = authProperties;
    this.keyManager = keyManager;
  }

  public Map<String, Object> health() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "OK");
    payload.put("authenticator", authenticator.name());
    payload.put("issuer", authProperties.getToken().getIssuer());
    payload.put("active_kid", keyManager.activeKid());
    return payload;
  }
}
