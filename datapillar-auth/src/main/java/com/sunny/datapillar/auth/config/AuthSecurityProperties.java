package com.sunny.datapillar.auth.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth security properties for binding security-related configuration.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@ConfigurationProperties(prefix = "security")
public class AuthSecurityProperties {

  private Csrf csrf = new Csrf();
  private Login login = new Login();
  private Password password = new Password();
  private List<String> trustedProxies = new ArrayList<>();

  @Data
  public static class Csrf {
    private boolean enabled = true;
    private String headerName = "X-CSRF-Token";
    private String cookieName = "csrf-token";
    private String refreshHeaderName = "X-Refresh-CSRF-Token";
    private String refreshCookieName = "refresh-csrf-token";
    private long ttlSeconds = 3600;
  }

  @Data
  public static class Login {
    private boolean enabled = true;
    private int maxAttempts = 5;
    private int windowSeconds = 600;
    private int lockSeconds = 900;
  }

  @Data
  public static class Password {
    private Argon2 argon2 = new Argon2();

    @Data
    public static class Argon2 {
      /** Argon2 salt length in bytes. */
      private int saltLength = 16;

      /** Argon2 hash length in bytes. */
      private int hashLength = 32;

      /** Argon2 parallelism. */
      private int parallelism = 1;

      /** Argon2 memory cost in MB. */
      private int memoryMb = 64;

      /** Argon2 iterations. */
      private int iterations = 3;
    }
  }
}
