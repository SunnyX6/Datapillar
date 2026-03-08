package com.sunny.datapillar.auth.key;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JWKS publisher for auth issuer. */
@Component
public class JwksPublisher {

  private final KeyManager keyManager;

  public JwksPublisher(KeyManager keyManager) {
    this.keyManager = keyManager;
  }

  public Map<String, Object> publish() {
    return Map.of("keys", List.copyOf(keyManager.publicJwks()));
  }
}
