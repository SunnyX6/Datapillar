package com.sunny.datapillar.auth.key;

import com.sunny.datapillar.auth.security.JwtToken;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import org.springframework.stereotype.Component;

/** Default key manager backed by runtime JwtToken configuration. */
@Component
public class DefaultKeyManager implements KeyManager {

  private final JwtToken jwtToken;

  public DefaultKeyManager(JwtToken jwtToken) {
    this.jwtToken = jwtToken;
  }

  @Override
  public String activeKid() {
    return jwtToken.getActiveKid();
  }

  @Override
  public PublicKey publicKey() {
    return jwtToken.getPublicKey();
  }

  @Override
  public PrivateKey privateKey() {
    return jwtToken.getPrivateKey();
  }

  @Override
  public String issuer() {
    return jwtToken.getIssuer();
  }

  @Override
  public List<String> audiences() {
    return jwtToken.getAudiences();
  }
}
