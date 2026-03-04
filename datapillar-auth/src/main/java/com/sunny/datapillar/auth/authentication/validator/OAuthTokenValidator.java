package com.sunny.datapillar.auth.authentication.validator;

import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;

/** Validator for external OAuth2/OIDC identity token. */
public interface OAuthTokenValidator {

  String name();

  boolean supports(SsoProviderConfig config);

  void validate(SsoProviderConfig config, SsoToken token);
}
