package com.sunny.datapillar.auth.service.login.method;

import com.sunny.datapillar.auth.authentication.validator.OAuthTokenValidator;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.entity.UserIdentity;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.UserIdentityMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginMethod;
import com.sunny.datapillar.auth.service.login.LoginMethodEnum;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.method.sso.SsoConfigReader;
import com.sunny.datapillar.auth.service.login.method.sso.SsoStateStore;
import com.sunny.datapillar.auth.service.login.method.sso.SsoStateStore.StatePayload;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoUserInfo;
import com.sunny.datapillar.auth.service.login.method.sso.provider.SsoProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * SSO-based login method.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SsoLoginMethod implements LoginMethod {

  private final SsoStateStore ssoStateStore;
  private final SsoConfigReader ssoConfigReader;
  private final UserIdentityMapper userIdentityMapper;
  private final UserMapper userMapper;
  private final TenantMapper tenantMapper;
  private final Map<String, SsoProvider> providerMap = new HashMap<>();
  private final List<OAuthTokenValidator> oauthTokenValidators;

  public SsoLoginMethod(
      SsoStateStore ssoStateStore,
      SsoConfigReader ssoConfigReader,
      UserIdentityMapper userIdentityMapper,
      UserMapper userMapper,
      TenantMapper tenantMapper,
      List<SsoProvider> providers,
      List<OAuthTokenValidator> oauthTokenValidators) {
    this.ssoStateStore = ssoStateStore;
    this.ssoConfigReader = ssoConfigReader;
    this.userIdentityMapper = userIdentityMapper;
    this.userMapper = userMapper;
    this.tenantMapper = tenantMapper;
    this.oauthTokenValidators = oauthTokenValidators;
    if (providers != null) {
      for (SsoProvider provider : providers) {
        providerMap.put(normalize(provider.provider()), provider);
      }
    }
  }

  @Override
  public String method() {
    return LoginMethodEnum.SSO.key();
  }

  public SsoQrResponse buildQr(Long tenantId, String provider) {
    throw new com.sunny.datapillar.common.exception.BadRequestException(
        "oauth2 authorize requires nonce and code_challenge");
  }

  public SsoQrResponse buildQr(
      Long tenantId,
      String provider,
      String nonce,
      String codeChallenge,
      String codeChallengeMethod) {
    if (tenantId == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    String normalizedProvider = normalize(provider);
    if (normalizedProvider == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    String state =
        ssoStateStore.createState(
            tenantId,
            normalizedProvider,
            nonce,
            codeChallenge,
            codeChallengeMethod == null ? "S256" : codeChallengeMethod);
    SsoProviderConfig config = ssoConfigReader.loadConfig(tenantId, normalizedProvider);
    return getProvider(normalizedProvider).buildQr(config, state);
  }

  @Override
  public LoginSubject authenticate(LoginCommand command) {
    if (command == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    String provider = normalize(command.getProvider());
    String code = trimToNull(command.getCode());
    String state = trimToNull(command.getState());
    String nonce = trimToNull(command.getNonce());
    String codeVerifier = trimToNull(command.getCodeVerifier());
    if (provider == null
        || code == null
        || state == null
        || nonce == null
        || codeVerifier == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }

    StatePayload statePayload =
        ssoStateStore.consumeOrThrow(state, null, provider, nonce, codeVerifier);
    Long tenantId = statePayload.getTenantId();
    if (tenantId == null || tenantId <= 0) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid SSO state");
    }

    Tenant tenant = tenantMapper.selectById(tenantId);
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Tenant not found: %s", String.valueOf(tenantId));
    }
    if (tenant.getStatus() == null || tenant.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant is disabled: tenantId=%s", tenantId);
    }

    SsoProviderConfig config = ssoConfigReader.loadConfig(tenantId, provider);
    SsoProvider ssoProvider = getProvider(provider);
    SsoToken token = ssoProvider.exchangeCode(config, code);
    validateInputToken(config, token);

    SsoUserInfo userInfo = ssoProvider.fetchUserInfo(config, token);
    String externalUserId = trimToNull(ssoProvider.extractExternalUserId(userInfo));
    if (externalUserId == null) {
      throw new com.sunny.datapillar.common.exception.InternalException(
          "SSO user identifier missing");
    }

    UserIdentity identity =
        userIdentityMapper.selectByProviderAndExternalUserId(tenantId, provider, externalUserId);
    if (identity == null) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }

    User user = userMapper.selectById(identity.getUserId());
    if (user == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "User not found: %s", identity.getUserId());
    }

    return LoginSubject.builder().user(user).tenant(tenant).loginMethod(method()).build();
  }

  private void validateInputToken(SsoProviderConfig config, SsoToken token) {
    if (oauthTokenValidators == null || oauthTokenValidators.isEmpty()) {
      return;
    }
    for (OAuthTokenValidator validator : oauthTokenValidators) {
      if (validator.supports(config)) {
        validator.validate(config, token);
        return;
      }
    }
  }

  private SsoProvider getProvider(String provider) {
    SsoProvider ssoProvider = providerMap.get(normalize(provider));
    if (ssoProvider == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "SSO provider not found: %s", provider);
    }
    return ssoProvider;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
