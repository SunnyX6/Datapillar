package com.sunny.datapillar.auth.api.wellknown;

import com.sunny.datapillar.auth.service.TokenAppService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Well-known endpoint controller. */
@RestController
public class WellKnownController {

  private final TokenAppService tokenAppService;

  public WellKnownController(TokenAppService tokenAppService) {
    this.tokenAppService = tokenAppService;
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    return tokenAppService.jwks();
  }
}
