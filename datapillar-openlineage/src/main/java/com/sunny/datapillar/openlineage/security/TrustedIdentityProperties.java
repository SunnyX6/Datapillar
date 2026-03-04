package com.sunny.datapillar.openlineage.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Trusted identity configuration. */
@Data
@ConfigurationProperties(prefix = "security.trusted-identity")
public class TrustedIdentityProperties {

  private boolean enabled = false;
}
