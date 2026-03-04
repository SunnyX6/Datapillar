package com.sunny.datapillar.auth.authentication.simple;

import com.sunny.datapillar.auth.authentication.AuthenticationRequest;
import com.sunny.datapillar.auth.authentication.AuthenticationResult;
import com.sunny.datapillar.auth.authentication.Authenticator;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.method.PasswordLoginMethod;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local username-password authenticator. */
@Component
@ConditionalOnProperty(
    prefix = "auth",
    name = "authenticator",
    havingValue = "simple",
    matchIfMissing = true)
public class SimpleAuthenticator implements Authenticator {

  private final PasswordLoginMethod passwordLoginMethod;

  public SimpleAuthenticator(PasswordLoginMethod passwordLoginMethod) {
    this.passwordLoginMethod = passwordLoginMethod;
  }

  @Override
  public String name() {
    return "simple";
  }

  @Override
  public AuthenticationResult authenticate(AuthenticationRequest request) {
    LoginCommand command = new LoginCommand();
    command.setMethod("password");
    command.setLoginAlias(request.getLoginAlias());
    command.setPassword(request.getPassword());
    command.setTenantCode(request.getTenantCode());
    command.setRememberMe(request.getRememberMe());
    command.setClientIp(request.getClientIp());

    LoginSubject subject = passwordLoginMethod.authenticate(command);
    return AuthenticationResult.builder()
        .authenticator(name())
        .subject(subject)
        .rememberMe(request.getRememberMe())
        .build();
  }
}
