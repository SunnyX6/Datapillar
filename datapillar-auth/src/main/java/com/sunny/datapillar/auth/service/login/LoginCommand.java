package com.sunny.datapillar.auth.service.login;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login command model.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
public class LoginCommand {

  @NotBlank(message = "method must not be blank")
  private String method;

  private Boolean rememberMe;

  private String loginAlias;

  private String password;

  private String tenantCode;

  private String provider;

  private String code;

  private String state;

  private String nonce;

  private String codeVerifier;

  @JsonIgnore private String clientIp;
}
