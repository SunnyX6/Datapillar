package com.sunny.datapillar.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Gravitino integration configuration. */
@Data
@Configuration
@ConfigurationProperties(prefix = "gravitino")
public class GravitinoConfig {

  /** Gravitino Service address */
  private String uri;

  /** SQL default metalake name */
  private String metalake;

  /** Semantic dataset catalog name. */
  private String semanticCatalog = "OneDS";

  /** Semantic dataset schema name. */
  private String semanticSchema = "OneDS";

  /** Optional simple auth user */
  private String simpleAuthUser;

  /** Connect timeout in milliseconds */
  private Integer connectTimeoutMs = 2000;

  /** Read timeout in milliseconds */
  private Integer readTimeoutMs = 5000;

  /** Auth configuration for Gravitino Java client options. */
  private AuthConfig auth = new AuthConfig();

  /** Auth root config. */
  @Data
  public static class AuthConfig {

    /** Auth type, supported values: simple, oauth, kerberos. */
    private String type = "simple";

    /** Simple auth settings. */
    private SimpleAuthConfig simple = new SimpleAuthConfig();

    /** OAuth auth settings. */
    private OAuthAuthConfig oauth = new OAuthAuthConfig();

    /** Kerberos auth settings. */
    private KerberosAuthConfig kerberos = new KerberosAuthConfig();
  }

  /** Simple auth settings. */
  @Data
  public static class SimpleAuthConfig {

    /** Gravitino simple auth user. */
    private String user;
  }

  /** OAuth auth settings. */
  @Data
  public static class OAuthAuthConfig {

    /** OAuth server URI. */
    private String serverUri;

    /** OAuth token path. */
    private String tokenPath;

    /** OAuth credential. */
    private String credential;

    /** OAuth scope. */
    private String scope;
  }

  /** Kerberos auth settings. */
  @Data
  public static class KerberosAuthConfig {

    /** Kerberos client principal. */
    private String clientPrincipal;

    /** Optional Kerberos keytab path. */
    private String keytabPath;
  }
}
