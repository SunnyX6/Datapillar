package com.sunny.datapillar.auth.api.wellknown;

import com.sunny.datapillar.auth.service.TokenAppService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** OpenID configuration endpoint controller. */
@RestController
@ConditionalOnProperty(prefix = "auth", name = "authenticator", havingValue = "oauth2")
public class OpenIdConfigurationController {

  private final TokenAppService tokenAppService;

  public OpenIdConfigurationController(TokenAppService tokenAppService) {
    this.tokenAppService = tokenAppService;
  }

  @GetMapping("/.well-known/openid-configuration")
  public Map<String, Object> openidConfiguration() {
    return tokenAppService.openidConfiguration();
  }
}
