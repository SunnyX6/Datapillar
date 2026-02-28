package com.sunny.datapillar.auth.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

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
import com.sunny.datapillar.auth.security.SessionStateStore;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import com.sunny.datapillar.common.security.SessionTokenClaims;
import com.sunny.datapillar.common.utils.JwtUtil;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 认证服务实现
 * 实现认证业务流程与规则校验
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
    private final JwtUtil jwtUtil;
    private final JwtToken jwtToken;
    private final SessionStateStore sessionStateStore;
    private final AuthCookieManager authCookieManager;
    private final UserAccessReader userAccessReader;

    /**
     * 刷新令牌并执行 refresh token 轮换。
     */
    @Override
    public LoginResponse refreshToken(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("refresh token 已过期");
        }

        try {
            Claims refreshClaims;
            try {
                refreshClaims = jwtUtil.parseToken(refreshToken);
            } catch (DatapillarRuntimeException e) {
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("refresh token 已过期");
            }

            String tokenType = jwtUtil.getTokenType(refreshClaims);
            if (!"refresh".equals(tokenType)) {
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token类型错误");
            }

            Long userId = jwtUtil.getUserId(refreshClaims);
            Long tenantId = jwtUtil.getTenantId(refreshClaims);
            if (userId == null || tenantId == null) {
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
            }

            String sid = jwtUtil.getSessionId(refreshClaims);
            String refreshJti = jwtUtil.getTokenId(refreshClaims);
            if (sid == null || sid.isBlank() || refreshJti == null || refreshJti.isBlank()) {
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
            }

            Boolean rememberMe = jwtUtil.getRememberMe(refreshClaims);
            if (!sessionStateStore.isSessionActive(sid)) {
                log.warn("security_event event=session_inactive_on_refresh sid={} tenantId={} userId={}", sid, tenantId, userId);
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已被撤销，请重新登录");
            }

            Tenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null) {
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("租户不存在: %s", String.valueOf(tenantId));
            }
            if (tenant.getStatus() == null || tenant.getStatus() != 1) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("租户已被禁用: tenantId=%s", tenantId);
            }

            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", userId);
            }
            validateUserStatus(user);

            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
            if (tenantUser == null) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
            }
            validateTenantUserStatus(tenantUser, tenantId, userId);

            String newAccessJti = UUID.randomUUID().toString();
            String newRefreshJti = UUID.randomUUID().toString();
            long accessTtlSeconds = jwtToken.getAccessTokenExpiration();
            long refreshTtlSeconds = jwtToken.getRefreshTokenExpiration(Boolean.TRUE.equals(rememberMe));

            Map<String, Object> accessClaims = new HashMap<>();
            accessClaims.put("roles", userAccessReader.loadRoleTypes(tenantId, userId));
            accessClaims.put(SessionTokenClaims.SESSION_ID, sid);
            accessClaims.put(SessionTokenClaims.TOKEN_ID, newAccessJti);
            String newAccessToken = jwtToken.generateAccessToken(
                    user.getId(), tenantId, user.getUsername(), user.getEmail(), accessClaims);
            String newRefreshToken = jwtToken.generateRefreshToken(
                    user.getId(), tenantId, rememberMe, sid, newRefreshJti);

            SessionStateStore.RotateResult rotateResult = sessionStateStore.rotateForRefresh(
                    sid,
                    refreshJti,
                    newRefreshJti,
                    newAccessJti,
                    refreshTtlSeconds,
                    accessTtlSeconds
            );
            if (rotateResult.sessionInactive()) {
                log.warn("security_event event=session_rotate_failed sid={} tenantId={} userId={} reason=session_inactive",
                        sid, tenantId, userId);
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已被撤销，请重新登录");
            }
            if (rotateResult.refreshReused()) {
                sessionStateStore.revokeSession(sid);
                log.warn("security_event event=refresh_token_reused sid={} tenantId={} userId={} jti={}",
                        sid, tenantId, userId, refreshJti);
                throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已被撤销，请重新登录");
            }

            authCookieManager.setAuthCookies(response, newAccessToken, newRefreshToken, rememberMe);
            authCookieManager.issueSessionCsrfCookies(tenantId, userId, refreshTtlSeconds, response);

            log.info("刷新令牌成功: tenantId={}, userId={}, username={}, sid={}",
                    tenantId, user.getId(), user.getUsername(), sid);

            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setUserId(user.getId());
            loginResponse.setTenantId(tenantId);
            loginResponse.setUsername(user.getUsername());
            loginResponse.setEmail(user.getEmail());
            return loginResponse;

        } catch (DatapillarRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("刷新令牌失败: {}", e.getMessage());
            throw new com.sunny.datapillar.common.exception.InternalException("Token刷新失败: %s", e.getMessage());
        }
    }

    /**
     * 清理认证 cookie。
     */
    @Override
    public void clearAuthCookies(HttpServletResponse response) {
        authCookieManager.clearAuthCookies(response);
    }

    /**
     * 校验 access token 的签名、租户/用户状态与在线会话状态。
     */
    @Override
    public TokenResponse validateToken(TokenRequest request) {
        Claims claims = parseAccessClaims(request.getToken());

        Long userId = jwtUtil.getUserId(claims);
        Long tenantId = jwtUtil.getTenantId(claims);
        String username = jwtUtil.getUsername(claims);
        String email = jwtUtil.getEmail(claims);

        if (userId == null || tenantId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("租户不存在: %s", String.valueOf(tenantId));
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("租户已被禁用: tenantId=%s", tenantId);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("用户不存在，请重新登录");
        }
        validateUserStatus(user);

        boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
        if (!impersonation) {
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
            if (tenantUser == null) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
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

    /**
     * 平台超管代入目标租户，替换当前会话 access token。
     */
    @Override
    public LoginResponse assumeTenant(Long tenantId, String accessToken, HttpServletResponse response) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }
        Claims claims = jwtUtil.parseToken(accessToken);
        String tokenType = jwtUtil.getTokenType(claims);
        if (!"access".equals(tokenType)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token类型错误");
        }

        String sid = jwtUtil.getSessionId(claims);
        String currentAccessJti = jwtUtil.getTokenId(claims);
        if (sid == null || sid.isBlank() || currentAccessJti == null || currentAccessJti.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }
        if (!sessionStateStore.isAccessTokenActive(sid, currentAccessJti)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已被撤销，请重新登录");
        }

        Long actorUserId = jwtUtil.getUserId(claims);
        Long actorTenantId = jwtUtil.getTenantId(claims);
        if (actorUserId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }
        if (actorTenantId == null || actorTenantId != 0L) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
        }

        User actor = userMapper.selectById(actorUserId);
        if (actor == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", actorUserId);
        }
        validateUserStatus(actor);

        List<RoleItem> systemRoles = userMapper.selectRolesByUserId(0L, actorUserId);
        boolean isAdmin = systemRoles != null && systemRoles.stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getType()));
        if (!isAdmin) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
        }

        Tenant targetTenant = tenantMapper.selectById(tenantId);
        if (targetTenant == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("租户不存在: %s", String.valueOf(tenantId));
        }
        if (targetTenant.getStatus() == null || targetTenant.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("租户已被禁用: tenantId=%s", tenantId);
        }

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("actorUserId", actorUserId);
        extraClaims.put("actorTenantId", 0L);
        extraClaims.put("impersonation", true);
        extraClaims.put("roles", List.of("ADMIN"));
        String newAccessJti = UUID.randomUUID().toString();
        extraClaims.put(SessionTokenClaims.SESSION_ID, sid);
        extraClaims.put(SessionTokenClaims.TOKEN_ID, newAccessJti);

        String newAccessToken = jwtToken.generateAccessToken(
                actorUserId, tenantId, actor.getUsername(), actor.getEmail(), extraClaims);
        boolean replaced = sessionStateStore.replaceAccessToken(
                sid,
                currentAccessJti,
                newAccessJti,
                jwtToken.getAccessTokenExpiration()
        );
        if (!replaced) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已被撤销，请重新登录");
        }
        authCookieManager.setAccessTokenCookie(response, newAccessToken);
        authCookieManager.issueBusinessCsrfCookie(tenantId, actorUserId, jwtToken.getAccessTokenExpiration(), response);

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setUserId(actor.getId());
        loginResponse.setTenantId(tenantId);
        loginResponse.setUsername(actor.getUsername());
        loginResponse.setEmail(actor.getEmail());
        return loginResponse;
    }

    /**
     * 返回 access token 的基础信息。
     */
    @Override
    public TokenInfoResponse getTokenInfo(String accessToken) {
        Claims claims = parseAccessClaims(accessToken);

        long expirationTime = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        long remainingSeconds = Math.max(0, (expirationTime - now) / 1000);
        if (remainingSeconds <= 0) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已过期");
        }

        Long userId = jwtUtil.getUserId(claims);
        Long tenantId = jwtUtil.getTenantId(claims);
        if (userId == null || tenantId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("租户不存在: %s", String.valueOf(tenantId));
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("租户已被禁用: tenantId=%s", tenantId);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("用户不存在，请重新登录");
        }
        validateUserStatus(user);

        boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
        if (!impersonation) {
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
            if (tenantUser == null) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
            }
            validateTenantUserStatus(tenantUser, tenantId, userId);
        }

        return TokenInfoResponse.builder()
                .remainingSeconds(remainingSeconds)
                .expirationTime(expirationTime)
                .issuedAt(claims.getIssuedAt().getTime())
                .userId(userId)
                .tenantId(tenantId)
                .username(jwtUtil.getUsername(claims))
                .build();
    }

    @Override
    public AuthenticationContextResponse resolveAuthenticationContext(String token) {
        Claims claims = parseAccessClaims(token);

        Long userId = jwtUtil.getUserId(claims);
        Long tenantId = jwtUtil.getTenantId(claims);
        if (userId == null || tenantId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("租户不存在: %s", String.valueOf(tenantId));
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("租户已被禁用: tenantId=%s", tenantId);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("用户不存在，请重新登录");
        }
        validateUserStatus(user);

        boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
        if (!impersonation) {
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
            if (tenantUser == null) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
            }
            validateTenantUserStatus(tenantUser, tenantId, userId);
        }

        String sid = jwtUtil.getSessionId(claims);
        String accessJti = jwtUtil.getTokenId(claims);
        return AuthenticationContextResponse.builder()
                .userId(userId)
                .tenantId(tenantId)
                .tenantCode(tenant.getCode())
                .tenantName(tenant.getName())
                .username(jwtUtil.getUsername(claims))
                .email(jwtUtil.getEmail(claims))
                .roles(EdDsaJwtSupport.toStringList(claims.get("roles")))
                .impersonation(impersonation)
                .actorUserId(jwtUtil.getActorUserId(claims))
                .actorTenantId(jwtUtil.getActorTenantId(claims))
                .sessionId(sid)
                .tokenId(accessJti)
                .build();
    }

    private Claims parseAccessClaims(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("缺少认证信息");
        }

        Claims claims = jwtUtil.parseToken(accessToken);
        String tokenType = jwtUtil.getTokenType(claims);
        if (!"access".equals(tokenType)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token类型错误");
        }

        String sid = jwtUtil.getSessionId(claims);
        String accessJti = jwtUtil.getTokenId(claims);
        if (sid == null || sid.isBlank() || accessJti == null || accessJti.isBlank()) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token无效");
        }
        if (!sessionStateStore.isAccessTokenActive(sid, accessJti)) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("Token已失效");
        }

        return claims;
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("用户已被禁用");
        }
    }

    private void validateTenantUserStatus(TenantUser tenantUser, Long tenantId, Long userId) {
        if (tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("租户成员已被禁用: tenantId=%s,userId=%s", tenantId, userId);
        }
    }

}
