package com.sunny.datapillar.auth.key;

import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.common.security.Ed25519JwkSupport;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JWKS publisher for auth issuer. */
@Component
public class JwksPublisher {

  private final KeyManager keyManager;
  private final AuthProperties authProperties;

  public JwksPublisher(KeyManager keyManager, AuthProperties authProperties) {
    this.keyManager = keyManager;
    this.authProperties = authProperties;
  }

  public Map<String, Object> publish() {
    if (!authProperties.getJwks().isEnabled()) {
      return Map.of("keys", List.of());
    }
    Map<String, Object> jwk =
        Ed25519JwkSupport.toJwk(keyManager.activeKid(), keyManager.publicKey());
    return Map.of("keys", List.of(jwk));
  }
}
