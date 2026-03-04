package com.sunny.datapillar.auth.authentication.oauth2;

import com.sunny.datapillar.auth.authentication.AuthenticationRequest;
import com.sunny.datapillar.auth.authentication.AuthenticationResult;
import com.sunny.datapillar.auth.authentication.Authenticator;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.method.SsoLoginMethod;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** OAuth2/OIDC authenticator. */
@Component
@ConditionalOnProperty(prefix = "auth", name = "authenticator", havingValue = "oauth2")
public class OAuth2Authenticator implements Authenticator {

  private final SsoLoginMethod ssoLoginMethod;
  private final TenantMapper tenantMapper;

  public OAuth2Authenticator(SsoLoginMethod ssoLoginMethod, TenantMapper tenantMapper) {
    this.ssoLoginMethod = ssoLoginMethod;
    this.tenantMapper = tenantMapper;
  }

  @Override
  public String name() {
    return "oauth2";
  }

  @Override
  public AuthenticationResult authenticate(AuthenticationRequest request) {
    LoginCommand command = new LoginCommand();
    command.setMethod("sso");
    command.setProvider(request.getProvider());
    command.setCode(request.getCode());
    command.setState(request.getState());
    command.setNonce(request.getNonce());
    command.setCodeVerifier(request.getCodeVerifier());
    command.setTenantCode(request.getTenantCode());
    command.setRememberMe(request.getRememberMe());
    command.setClientIp(request.getClientIp());

    LoginSubject subject = ssoLoginMethod.authenticate(command);
    return AuthenticationResult.builder()
        .authenticator(name())
        .subject(subject)
        .rememberMe(request.getRememberMe())
        .build();
  }

  @Override
  public SsoQrResponse authorize(AuthenticationRequest request) {
    String tenantCode = normalize(request.getTenantCode());
    if (tenantCode == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "tenant_code must not be blank");
    }
    String provider = normalize(request.getProvider());
    if (provider == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "provider must not be blank");
    }
    String nonce = normalize(request.getNonce());
    if (nonce == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "nonce must not be blank");
    }
    String codeChallenge = normalize(request.getCodeChallenge());
    if (codeChallenge == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "code_challenge must not be blank");
    }

    Tenant tenant = tenantMapper.selectByCode(tenantCode);
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Tenant not found: %s", tenantCode);
    }
    if (tenant.getStatus() == null || tenant.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant is disabled: tenantId=%s", tenant.getId());
    }

    String codeChallengeMethod = normalize(request.getCodeChallengeMethod());
    return ssoLoginMethod.buildQr(
        tenant.getId(),
        provider,
        nonce,
        codeChallenge,
        codeChallengeMethod == null ? "S256" : codeChallengeMethod);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
