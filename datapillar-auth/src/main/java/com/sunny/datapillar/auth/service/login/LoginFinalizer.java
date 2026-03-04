package com.sunny.datapillar.auth.service.login;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.auth.session.SessionStore;
import com.sunny.datapillar.auth.token.TokenClaims;
import com.sunny.datapillar.auth.token.TokenEngine;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Finalizer for post-auth login completion.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class LoginFinalizer {

  private final TenantMapper tenantMapper;
  private final TenantUserMapper tenantUserMapper;
  private final TokenEngine tokenEngine;
  private final SessionStore sessionStore;
  private final AuthCookieManager authCookieManager;
  private final UserAccessReader userAccessReader;
  private final LoginTokenStore loginTokenStore;

  public LoginResultResponse finalize(
      LoginSubject subject, Boolean rememberMe, HttpServletResponse response) {
    if (subject == null || subject.getUser() == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }
    if (subject.requiresTenantSelection()) {
      return buildTenantSelectResult(subject, rememberMe, response);
    }

    User user = subject.getUser();
    Tenant tenant = subject.getTenant();
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Invalid parameters");
    }

    validateUserStatus(user);
    validateTenantStatus(tenant);
    validateTenantUser(tenant.getId(), user.getId());

    List<TenantOptionItem> tenantOptions = resolveTenantOptions(user.getId(), tenant.getId());
    List<String> tenantCodes = tenantOptions.stream().map(TenantOptionItem::getTenantCode).toList();

    String sid = UUID.randomUUID().toString();
    String accessJti = UUID.randomUUID().toString();
    String refreshJti = UUID.randomUUID().toString();

    TokenClaims accessClaims =
        TokenClaims.builder()
            .userId(user.getId())
            .tenantId(tenant.getId())
            .tenantCode(tenant.getCode())
            .tenantCodes(tenantCodes)
            .preferredUsername(user.getUsername())
            .email(user.getEmail())
            .roles(userAccessReader.loadRoleTypes(tenant.getId(), user.getId()))
            .sessionId(sid)
            .tokenId(accessJti)
            .tokenType("access")
            .impersonation(false)
            .build();
    String accessToken = tokenEngine.issueAccessToken(accessClaims);

    TokenClaims refreshClaims =
        TokenClaims.builder()
            .userId(user.getId())
            .tenantId(tenant.getId())
            .tenantCode(tenant.getCode())
            .sessionId(sid)
            .tokenId(refreshJti)
            .tokenType("refresh")
            .rememberMe(Boolean.TRUE.equals(rememberMe))
            .build();
    String refreshToken = tokenEngine.issueRefreshToken(refreshClaims);

    long accessTtlSeconds = tokenEngine.accessTokenTtlSeconds();
    long refreshTtlSeconds = tokenEngine.refreshTokenTtlSeconds(Boolean.TRUE.equals(rememberMe));

    sessionStore.openSession(
        sid,
        tenant.getId(),
        user.getId(),
        accessJti,
        refreshJti,
        refreshTtlSeconds,
        accessTtlSeconds);

    authCookieManager.setAuthCookies(response, accessToken, refreshToken, rememberMe);
    authCookieManager.issueSessionCsrfCookies(
        tenant.getId(), user.getId(), refreshTtlSeconds, response);

    LoginResponse loginResponse = userAccessReader.buildLoginResponse(tenant.getId(), user);
    return buildLoginResult(loginResponse, tenantOptions);
  }

  private LoginResultResponse buildTenantSelectResult(
      LoginSubject subject, Boolean rememberMe, HttpServletResponse response) {
    List<TenantOptionItem> options =
        subject.getTenantOptions() == null ? new ArrayList<>() : subject.getTenantOptions();
    if (options.isEmpty()) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }
    LoginTokenStore.LoginTokenPayload payload = new LoginTokenStore.LoginTokenPayload();
    payload.setUserId(subject.getUser().getId());
    payload.setTenantIds(options.stream().map(TenantOptionItem::getTenantId).toList());
    payload.setRememberMe(Boolean.TRUE.equals(rememberMe));
    payload.setLoginMethod(subject.getLoginMethod());
    String loginToken = loginTokenStore.issue(payload);
    authCookieManager.setLoginTokenCookie(response, loginToken, loginTokenStore.ttlSeconds());
    LoginResultResponse result = new LoginResultResponse();
    result.setLoginStage("TENANT_SELECT");
    result.setTenants(options);
    return result;
  }

  private LoginResultResponse buildLoginResult(
      LoginResponse loginResponse, List<TenantOptionItem> tenantOptions) {
    LoginResultResponse result = new LoginResultResponse();
    result.setTenants(tenantOptions);
    result.setUserId(loginResponse.getUserId());
    result.setUsername(loginResponse.getUsername());
    result.setEmail(loginResponse.getEmail());
    result.setRoles(loginResponse.getRoles());
    result.setMenus(loginResponse.getMenus());
    return result;
  }

  private List<TenantOptionItem> resolveTenantOptions(Long userId, Long currentTenantId) {
    List<TenantOptionItem> options = tenantUserMapper.selectTenantOptionsByUserId(userId);
    List<TenantOptionItem> normalized =
        options == null ? new ArrayList<>() : new ArrayList<>(options);
    int selectedIndex = -1;
    for (int index = 0; index < normalized.size(); index++) {
      TenantOptionItem option = normalized.get(index);
      if (option != null && currentTenantId.equals(option.getTenantId())) {
        selectedIndex = index;
        break;
      }
    }

    if (selectedIndex >= 0) {
      if (selectedIndex > 0) {
        TenantOptionItem selected = normalized.remove(selectedIndex);
        normalized.add(0, selected);
      }
      return normalized;
    }

    Tenant tenant = tenantMapper.selectById(currentTenantId);
    TenantOptionItem fallback = new TenantOptionItem();
    fallback.setTenantId(currentTenantId);
    fallback.setTenantCode(tenant == null ? String.valueOf(currentTenantId) : tenant.getCode());
    fallback.setTenantName(tenant == null ? String.valueOf(currentTenantId) : tenant.getCode());
    fallback.setStatus(tenant == null ? 1 : tenant.getStatus());
    fallback.setIsDefault(1);
    normalized.add(0, fallback);
    return normalized;
  }

  private void validateUserStatus(User user) {
    if (user.getStatus() == null || user.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("User is disabled");
    }
  }

  private void validateTenantStatus(Tenant tenant) {
    if (tenant.getStatus() == null || tenant.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant is disabled: tenantId=%s", tenant.getId());
    }
  }

  private void validateTenantUser(Long tenantId, Long userId) {
    TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
    if (tenantUser == null) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }
    if (tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant membership is disabled: tenantId=%s,userId=%s", tenantId, userId);
    }
  }
}
