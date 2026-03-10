package com.sunny.datapillar.studio.security.apikey;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Secure generator for tenant API key plaintext values. */
@Component
public class TenantApiKeyGenerator {

  private static final String KEY_PREFIX = "dpk_";
  private static final int RANDOM_BYTES = 32;

  private final SecureRandom secureRandom = new SecureRandom();

  public String generate() {
    byte[] randomBytes = new byte[RANDOM_BYTES];
    secureRandom.nextBytes(randomBytes);
    return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
