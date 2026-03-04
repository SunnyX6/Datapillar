package com.sunny.datapillar.auth.service.impl;

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
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.security.JwtToken;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.auth.session.SessionStore;
import com.sunny.datapillar.auth.token.TokenClaims;
import com.sunny.datapillar.auth.token.TokenEngine;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Auth service implementation.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserMapper userMapper;
  private final TenantMapper tenantMapper;
  private final TenantUserMapper tenantUserMapper;
  private final JwtToken jwtToken;
  private final TokenEngine tokenEngine;
  private final SessionStore sessionStore;
  private final AuthCookieManager authCookieManager;
  private final UserAccessReader userAccessReader;

  /** Refresh tokens and rotate refresh token. */
  @Override
  public LoginResponse refreshToken(String refreshToken, HttpServletResponse response) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Refresh token has expired");
    }

    try {
      Claims refreshClaims;
      try {
        refreshClaims = jwtToken.parseToken(refreshToken);
      } catch (DatapillarRuntimeException e) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "Refresh token has expired");
      }

      String tokenType = jwtToken.getTokenType(refreshClaims);
      if (!"refresh".equals(tokenType)) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token type");
      }

      Long userId = jwtToken.getUserId(refreshClaims);
      Long tenantId = jwtToken.getTenantId(refreshClaims);
      if (userId == null || tenantId == null) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
      }

      String sid = jwtToken.getSessionId(refreshClaims);
      String refreshJti = jwtToken.getTokenId(refreshClaims);
      if (sid == null || sid.isBlank() || refreshJti == null || refreshJti.isBlank()) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
      }

      Boolean rememberMe = jwtToken.getRememberMe(refreshClaims);
      if (!sessionStore.isSessionActive(sid)) {
        log.warn(
            "security_event event=session_inactive_on_refresh sid={} tenantId={} userId={}",
            sid,
            tenantId,
            userId);
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "Token has been revoked, please log in again");
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

      User user = userMapper.selectById(userId);
      if (user == null) {
        throw new com.sunny.datapillar.common.exception.NotFoundException(
            "User not found: %s", userId);
      }
      validateUserStatus(user);

      TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
      if (tenantUser == null) {
        throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
      }
      validateTenantUserStatus(tenantUser, tenantId, userId);

      String newAccessJti = UUID.randomUUID().toString();
      String newRefreshJti = UUID.randomUUID().toString();
      long accessTtlSeconds = tokenEngine.accessTokenTtlSeconds();
      long refreshTtlSeconds = tokenEngine.refreshTokenTtlSeconds(Boolean.TRUE.equals(rememberMe));

      TokenClaims newAccessClaims =
          TokenClaims.builder()
              .userId(user.getId())
              .tenantId(tenantId)
              .tenantCode(tenant.getCode())
              .tenantCodes(List.of(tenant.getCode()))
              .preferredUsername(user.getUsername())
              .email(user.getEmail())
              .roles(userAccessReader.loadRoleTypes(tenantId, userId))
              .sessionId(sid)
              .tokenId(newAccessJti)
              .tokenType("access")
              .impersonation(false)
              .build();
      String newAccessToken = tokenEngine.issueAccessToken(newAccessClaims);
      TokenClaims newRefreshClaims =
          TokenClaims.builder()
              .userId(user.getId())
              .tenantId(tenantId)
              .tenantCode(tenant.getCode())
              .sessionId(sid)
              .tokenId(newRefreshJti)
              .tokenType("refresh")
              .rememberMe(Boolean.TRUE.equals(rememberMe))
              .build();
      String newRefreshToken = tokenEngine.issueRefreshToken(newRefreshClaims);

      SessionStore.RotateResult rotateResult =
          sessionStore.rotateForRefresh(
              sid, refreshJti, newRefreshJti, newAccessJti, refreshTtlSeconds, accessTtlSeconds);
      if (rotateResult.sessionInactive()) {
        log.warn(
            "security_event event=session_rotate_failed sid={} tenantId={} userId={} reason=session_inactive",
            sid,
            tenantId,
            userId);
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "Token has been revoked, please log in again");
      }
      if (rotateResult.refreshReused()) {
        sessionStore.revokeSession(sid);
        log.warn(
            "security_event event=refresh_token_reused sid={} tenantId={} userId={} jti={}",
            sid,
            tenantId,
            userId,
            refreshJti);
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "Token has been revoked, please log in again");
      }

      authCookieManager.setAuthCookies(response, newAccessToken, newRefreshToken, rememberMe);
      authCookieManager.issueSessionCsrfCookies(tenantId, userId, refreshTtlSeconds, response);

      log.info(
          "Token refresh succeeded: tenantId={}, userId={}, username={}, sid={}",
          tenantId,
          user.getId(),
          user.getUsername(),
          sid);

      LoginResponse loginResponse = new LoginResponse();
      loginResponse.setUserId(user.getId());
      loginResponse.setTenantId(tenantId);
      loginResponse.setUsername(user.getUsername());
      loginResponse.setEmail(user.getEmail());
      return loginResponse;

    } catch (DatapillarRuntimeException e) {
      throw e;
    } catch (Throwable e) {
      log.error("Token refresh failed: {}", e.getMessage());
      throw new com.sunny.datapillar.common.exception.InternalException(
          "Token refresh failed: %s", e.getMessage());
    }
  }

  /** Clear auth cookies. */
  @Override
  public void clearAuthCookies(HttpServletResponse response) {
    authCookieManager.clearAuthCookies(response);
  }

  /** Validate access-token signature, tenant/user status, and online session state. */
  @Override
  public TokenResponse validateToken(TokenRequest request) {
    Claims claims = parseAccessClaims(request.getToken());

    Long userId = jwtToken.getUserId(claims);
    Long tenantId = jwtToken.getTenantId(claims);
    String username = jwtToken.getUsername(claims);
    String email = jwtToken.getEmail(claims);

    if (userId == null || tenantId == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
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

    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "User not found, please log in again");
    }
    validateUserStatus(user);

    boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
    if (!impersonation) {
      TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
      if (tenantUser == null) {
        throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
      }
      validateTenantUserStatus(tenantUser, tenantId, userId);
    }

    TokenResponse response = new TokenResponse();
    response.setValid(true);
    response.setUserId(userId);
    response.setTenantId(tenantId);
    response.setUsername(username);
    response.setEmail(email);
    return response;
  }

  /** Impersonate target tenant as platform admin and replace current access token. */
  @Override
  public LoginResponse assumeTenant(
      Long tenantId, String accessToken, HttpServletResponse response) {
    if (accessToken == null || accessToken.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
    }
    Claims claims = jwtToken.parseToken(accessToken);
    String tokenType = jwtToken.getTokenType(claims);
    if (!"access".equals(tokenType)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token type");
    }

    String sid = jwtToken.getSessionId(claims);
    String currentAccessJti = jwtToken.getTokenId(claims);
    if (sid == null || sid.isBlank() || currentAccessJti == null || currentAccessJti.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
    }
    if (!sessionStore.isAccessTokenActive(sid, currentAccessJti)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Token has been revoked, please log in again");
    }

    Long actorUserId = jwtToken.getUserId(claims);
    Long actorTenantId = jwtToken.getTenantId(claims);
    if (actorUserId == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
    }
    if (actorTenantId == null || actorTenantId != 0L) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }

    User actor = userMapper.selectById(actorUserId);
    if (actor == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "User not found: %s", actorUserId);
    }
    validateUserStatus(actor);

    List<RoleItem> systemRoles = userMapper.selectRolesByUserId(0L, actorUserId);
    boolean isAdmin =
        systemRoles != null
            && systemRoles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getType()));
    if (!isAdmin) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
    }

    Tenant targetTenant = tenantMapper.selectById(tenantId);
    if (targetTenant == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Tenant not found: %s", String.valueOf(tenantId));
    }
    if (targetTenant.getStatus() == null || targetTenant.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant is disabled: tenantId=%s", tenantId);
    }

    String newAccessJti = UUID.randomUUID().toString();
    TokenClaims accessClaims =
        TokenClaims.builder()
            .userId(actorUserId)
            .tenantId(tenantId)
            .tenantCode(targetTenant.getCode())
            .tenantCodes(List.of(targetTenant.getCode()))
            .preferredUsername(actor.getUsername())
            .email(actor.getEmail())
            .roles(List.of("ADMIN"))
            .sessionId(sid)
            .tokenId(newAccessJti)
            .tokenType("access")
            .impersonation(true)
            .actorUserId(actorUserId)
            .actorTenantId(0L)
            .build();
    String newAccessToken = tokenEngine.issueAccessToken(accessClaims);
    long accessTtlSeconds = tokenEngine.accessTokenTtlSeconds();
    boolean replaced =
        sessionStore.replaceAccessToken(sid, currentAccessJti, newAccessJti, accessTtlSeconds);
    if (!replaced) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Token has been revoked, please log in again");
    }
    authCookieManager.setAccessTokenCookie(response, newAccessToken);
    authCookieManager.issueBusinessCsrfCookie(tenantId, actorUserId, accessTtlSeconds, response);

    LoginResponse loginResponse = new LoginResponse();
    loginResponse.setUserId(actor.getId());
    loginResponse.setTenantId(tenantId);
    loginResponse.setUsername(actor.getUsername());
    loginResponse.setEmail(actor.getEmail());
    return loginResponse;
  }

  /** Return basic info for the access token. */
  @Override
  public TokenInfoResponse getTokenInfo(String accessToken) {
    Claims claims = parseAccessClaims(accessToken);

    long expirationTime = claims.getExpiration().getTime();
    long now = System.currentTimeMillis();
    long remainingSeconds = Math.max(0, (expirationTime - now) / 1000);
    if (remainingSeconds <= 0) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token has expired");
    }

    Long userId = jwtToken.getUserId(claims);
    Long tenantId = jwtToken.getTenantId(claims);
    if (userId == null || tenantId == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
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

    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "User not found, please log in again");
    }
    validateUserStatus(user);

    boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
    if (!impersonation) {
      TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
      if (tenantUser == null) {
        throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
      }
      validateTenantUserStatus(tenantUser, tenantId, userId);
    }

    return TokenInfoResponse.builder()
        .remainingSeconds(remainingSeconds)
        .expirationTime(expirationTime)
        .issuedAt(claims.getIssuedAt().getTime())
        .userId(userId)
        .tenantId(tenantId)
        .username(jwtToken.getUsername(claims))
        .build();
  }

  @Override
  public AuthenticationContextResponse resolveAuthenticationContext(String token) {
    Claims claims = parseAccessClaims(token);

    Long userId = jwtToken.getUserId(claims);
    Long tenantId = jwtToken.getTenantId(claims);
    if (userId == null || tenantId == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
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

    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "User not found, please log in again");
    }
    validateUserStatus(user);

    boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
    if (!impersonation) {
      TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
      if (tenantUser == null) {
        throw new com.sunny.datapillar.common.exception.ForbiddenException("Access denied");
      }
      validateTenantUserStatus(tenantUser, tenantId, userId);
    }

    String sid = jwtToken.getSessionId(claims);
    String accessJti = jwtToken.getTokenId(claims);
    return AuthenticationContextResponse.builder()
        .userId(userId)
        .tenantId(tenantId)
        .tenantCode(tenant.getCode())
        .tenantName(tenant.getName())
        .username(jwtToken.getUsername(claims))
        .email(jwtToken.getEmail(claims))
        .roles(EdDsaJwtSupport.toStringList(claims.get("roles")))
        .impersonation(impersonation)
        .actorUserId(jwtToken.getActorUserId(claims))
        .actorTenantId(jwtToken.getActorTenantId(claims))
        .sessionId(sid)
        .tokenId(accessJti)
        .build();
  }

  private Claims parseAccessClaims(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Missing authentication info");
    }

    Claims claims = jwtToken.parseToken(accessToken);
    String tokenType = jwtToken.getTokenType(claims);
    if (!"access".equals(tokenType)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token type");
    }

    String sid = jwtToken.getSessionId(claims);
    String accessJti = jwtToken.getTokenId(claims);
    if (sid == null || sid.isBlank() || accessJti == null || accessJti.isBlank()) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Invalid token");
    }
    if (!sessionStore.isAccessTokenActive(sid, accessJti)) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "Token is no longer valid");
    }

    return claims;
  }

  private void validateUserStatus(User user) {
    if (user.getStatus() == null || user.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("User is disabled");
    }
  }

  private void validateTenantUserStatus(TenantUser tenantUser, Long tenantId, Long userId) {
    if (tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "Tenant membership is disabled: tenantId=%s,userId=%s", tenantId, userId);
    }
  }
}
