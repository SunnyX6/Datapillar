package com.sunny.datapillar.auth.service.impl;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.exception.login.LoginMethodNotSupportedException;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.security.CsrfTokenStore;
import com.sunny.datapillar.auth.security.JwtToken;
import com.sunny.datapillar.auth.service.LoginService;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginFinalizer;
import com.sunny.datapillar.auth.service.login.LoginMethod;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.LoginTokenStore;
import com.sunny.datapillar.auth.service.login.method.SsoLoginMethod;
import com.sunny.datapillar.auth.session.SessionStore;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Login service implementation.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

  private final UserMapper userMapper;
  private final TenantMapper tenantMapper;
  private final JwtToken jwtToken;
  private final SessionStore sessionStore;
  private final CsrfTokenStore csrfTokenStore;
  private final AuthCookieManager authCookieManager;
  private final LoginFinalizer loginFinalizer;
  private final LoginTokenStore loginTokenStore;
  private final SsoLoginMethod ssoLoginMethod;
  private final Map<String, LoginMethod> loginMethodMap = new HashMap<>();

  public LoginServiceImpl(
      UserMapper userMapper,
      TenantMapper tenantMapper,
      JwtToken jwtToken,
      SessionStore sessionStore,
      CsrfTokenStore csrfTokenStore,
      AuthCookieManager authCookieManager,
      LoginFinalizer loginFinalizer,
      LoginTokenStore loginTokenStore,
      SsoLoginMethod ssoLoginMethod,
      List<LoginMethod> loginMethods) {
    this.userMapper = userMapper;
    this.tenantMapper = tenantMapper;
    this.jwtToken = jwtToken;
    this.sessionStore = sessionStore;
    this.csrfTokenStore = csrfTokenStore;
    this.authCookieManager = authCookieManager;
    this.loginFinalizer = loginFinalizer;
    this.loginTokenStore = loginTokenStore;
    this.ssoLoginMethod = ssoLoginMethod;
    if (loginMethods != null) {
      for (LoginMethod method : loginMethods) {
        loginMethodMap.put(normalize(method.method()), method);
      }
    }
  }

  @Override
  public LoginResultResponse login(
      LoginCommand command, String clientIp, HttpServletResponse response) {
    if (command != null && !StringUtils.hasText(command.getClientIp())) {
      command.setClientIp(clientIp);
    }
    String requestedMethod = normalize(command == null ? null : command.getMethod());
    LoginMethod method = loginMethodMap.get(requestedMethod);
    if (method == null) {
      throw new LoginMethodNotSupportedException(requestedMethod);
    }
    LoginSubject subject = method.authenticate(command);
    return loginFinalizer.finalize(
        subject, command == null ? null : command.getRememberMe(), response);
  }

  @Override
  public LoginResultResponse loginWithTenant(
      String loginToken, Long tenantId, HttpServletResponse response) {
    if (tenantId == null || tenantId <= 0) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    LoginTokenStore.LoginTokenPayload payload = loginTokenStore.consumeOrThrow(loginToken);
    List<Long> allowedTenants = payload.getTenantIds();
    if (allowedTenants == null || !allowedTenants.contains(tenantId)) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }

    User user = userMapper.selectById(payload.getUserId());
    if (user == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "User not found: %s", payload.getUserId());
    }
    Tenant tenant = tenantMapper.selectById(tenantId);
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Tenant not found: %s", String.valueOf(tenantId));
    }

    LoginSubject subject =
        LoginSubject.builder()
            .user(user)
            .tenant(tenant)
            .loginMethod(payload.getLoginMethod())
            .build();
    LoginResultResponse result =
        loginFinalizer.finalize(subject, payload.getRememberMe(), response);
    authCookieManager.clearLoginTokenCookie(response);
    return result;
  }

  @Override
  public SsoQrResponse getSsoQr(String tenantCode, String provider) {
    if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(provider)) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    Tenant tenant = loadTenant(tenantCode);
    return ssoLoginMethod.buildQr(tenant.getId(), provider);
  }

  @Override
  public void logout(String accessToken, HttpServletResponse response) {
    try {
      if (StringUtils.hasText(accessToken)) {
        Claims claims = jwtToken.parseToken(accessToken);
        Long userId = jwtToken.getUserId(claims);
        Long tenantId = jwtToken.getTenantId(claims);
        if (userId == null || tenantId == null) {
          throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
        }

        boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
        String sid = jwtToken.getSessionId(claims);
        String jti = jwtToken.getTokenId(claims);

        if (StringUtils.hasText(sid)) {
          sessionStore.revokeSession(sid);
          log.info(
              "security_event event=logout_revoke_session sid={} tenantId={} userId={} jti={} impersonation={}",
              sid,
              tenantId,
              userId,
              jti,
              impersonation);
        } else if (StringUtils.hasText(jti)) {
          sessionStore.revokeAccessToken(jti);
          log.info(
              "security_event event=logout_revoke_access_token tenantId={} userId={} jti={} impersonation={}",
              tenantId,
              userId,
              jti,
              impersonation);
        }

        csrfTokenStore.clearToken(tenantId, userId);
        csrfTokenStore.clearRefreshToken(tenantId, userId);
      }
    } catch (DatapillarRuntimeException ex) {
      String message = ex.getMessage();
      if (message != null
          && (message.contains("Invalid token")
              || message.contains("Token has expired")
              || message.contains("Invalid token type"))) {
        log.warn("security_event event=logout_ignore_invalid_token message={}", message);
      } else {
        throw ex;
      }
    } finally {
      authCookieManager.clearAuthCookies(response);
      authCookieManager.clearLoginTokenCookie(response);
    }
  }

  private Tenant loadTenant(String tenantCode) {
    String normalizedTenantCode = tenantCode.trim();
    Tenant tenant = tenantMapper.selectByCode(normalizedTenantCode);
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Tenant not found: %s", normalizedTenantCode);
    }
    if (tenant.getStatus() == null || tenant.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant is disabled: tenantId=%s", tenant.getId());
    }
    return tenant;
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
