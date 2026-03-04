package com.sunny.datapillar.auth.service.login.method.sso.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO token model.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoToken {
  private String accessToken;
  private Long expiresIn;
  private Map<String, Object> raw;
}
