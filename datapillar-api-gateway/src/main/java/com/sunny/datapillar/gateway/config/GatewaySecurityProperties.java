package com.sunny.datapillar.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway security configuration properties Carrying gateway security configuration items and
 * completing parameter binding
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@ConfigurationProperties(prefix = "security")
public class GatewaySecurityProperties {

  private boolean requireHttps = false;
  private List<String> trustedProxies = new ArrayList<>();
  private Headers headers = new Headers();

  @Data
  public static class Headers {
    private boolean enabled = true;
    private long hstsMaxAgeSeconds = 31536000;
    private boolean includeSubDomains = true;
  }
}
