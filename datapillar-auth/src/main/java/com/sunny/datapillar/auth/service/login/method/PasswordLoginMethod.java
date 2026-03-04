package com.sunny.datapillar.auth.service.login.method;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.LoginAttemptTracker;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginMethod;
import com.sunny.datapillar.auth.service.login.LoginMethodEnum;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password-based login method.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class PasswordLoginMethod implements LoginMethod {

  private final UserMapper userMapper;
  private final TenantMapper tenantMapper;
  private final TenantUserMapper tenantUserMapper;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptTracker loginAttemptTracker;

  @Override
  public String method() {
    return LoginMethodEnum.PASSWORD.key();
  }

  @Override
  public LoginSubject authenticate(LoginCommand command) {
    if (command == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    String loginAlias = trimToNull(command.getLoginAlias());
    String password = trimToNull(command.getPassword());
    if (loginAlias == null || password == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }

    String tenantCode = trimToNull(command.getTenantCode());
    String clientIp = trimToNull(command.getClientIp());
    loginAttemptTracker.assertLoginAllowed(tenantCode, loginAlias, clientIp);

    User user = findUserByLoginAlias(loginAlias);
    if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      loginAttemptTracker.recordFailure(tenantCode, loginAlias, clientIp);
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Invalid username or password");
    }
    if (user.getStatus() == null || user.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("User is disabled");
    }
    loginAttemptTracker.clearFailures(tenantCode, loginAlias, clientIp);

    if (tenantCode != null) {
      Tenant tenant = tenantMapper.selectByCode(tenantCode);
      if (tenant == null) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "Tenant not found: %s", tenantCode);
      }
      if (tenant.getStatus() == null || tenant.getStatus() != 1) {
        throw new com.sunny.datapillar.common.exception.ForbiddenException(
            "Tenant is disabled: tenantId=%s", tenant.getId());
      }
      return LoginSubject.builder().user(user).tenant(tenant).loginMethod(method()).build();
    }

    List<TenantOptionItem> options = tenantUserMapper.selectTenantOptionsByUserId(user.getId());
    if (options == null || options.isEmpty()) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }
    if (options.size() == 1) {
      TenantOptionItem option = options.get(0);
      Tenant tenant = tenantMapper.selectById(option.getTenantId());
      if (tenant == null) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "Tenant not found: %s", String.valueOf(option.getTenantId()));
      }
      if (tenant.getStatus() == null || tenant.getStatus() != 1) {
        throw new com.sunny.datapillar.common.exception.ForbiddenException(
            "Tenant is disabled: tenantId=%s", tenant.getId());
      }
      return LoginSubject.builder().user(user).tenant(tenant).loginMethod(method()).build();
    }

    return LoginSubject.builder().user(user).tenantOptions(options).loginMethod(method()).build();
  }

  private User findUserByLoginAlias(String loginAlias) {
    if (loginAlias.contains("@")) {
      return userMapper.selectByEmail(loginAlias);
    }
    return userMapper.selectByUsername(loginAlias);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
