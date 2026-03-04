package com.sunny.datapillar.studio.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Trusted identity configuration properties. */
@Data
@ConfigurationProperties(prefix = "security.trusted-identity")
public class TrustedIdentityProperties {

  private boolean enabled = false;
}
