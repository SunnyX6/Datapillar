package com.sunny.datapillar.auth.service.login;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.security.JwtToken;
import com.sunny.datapillar.auth.security.SessionStateStore;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.security.SessionTokenClaims;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;

/**
 * 登录Finalizer组件
 * 负责登录Finalizer核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class LoginFinalizer {

    private final TenantMapper tenantMapper;
    private final TenantUserMapper tenantUserMapper;
    private final JwtToken jwtToken;
    private final SessionStateStore sessionStateStore;
    private final AuthCookieManager authCookieManager;
    private final UserAccessReader userAccessReader;
    private final LoginTokenStore loginTokenStore;

    public AuthDto.LoginResult finalize(LoginSubject subject, Boolean rememberMe, HttpServletResponse response) {
        if (subject == null || subject.getUser() == null) {
            throw new BadRequestException("参数错误");
        }
        if (subject.requiresTenantSelection()) {
            return buildTenantSelectResult(subject, rememberMe, response);
        }

        User user = subject.getUser();
        Tenant tenant = subject.getTenant();
        if (tenant == null) {
            throw new BadRequestException("参数错误");
        }

        validateUserStatus(user);
        validateTenantStatus(tenant);
        validateTenantUser(tenant.getId(), user.getId());

        List<AuthDto.TenantOption> tenantOptions = resolveTenantOptions(user.getId(), tenant.getId());

        String sid = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        Map<String, Object> accessClaims = buildAccessClaims(tenant.getId(), user.getId(), sid, accessJti);
        String accessToken = jwtToken.generateAccessToken(
                user.getId(), tenant.getId(), user.getUsername(), user.getEmail(), accessClaims);
        String refreshToken = jwtToken.generateRefreshToken(
                user.getId(), tenant.getId(), rememberMe, sid, refreshJti);

        long accessTtlSeconds = jwtToken.getAccessTokenExpiration();
        long refreshTtlSeconds = jwtToken.getRefreshTokenExpiration(Boolean.TRUE.equals(rememberMe));

        sessionStateStore.openSession(
                sid,
                tenant.getId(),
                user.getId(),
                accessJti,
                refreshJti,
                refreshTtlSeconds,
                accessTtlSeconds
        );

        authCookieManager.setAuthCookies(response, accessToken, refreshToken, rememberMe);
        authCookieManager.issueSessionCsrfCookies(tenant.getId(), user.getId(), refreshTtlSeconds, response);

        AuthDto.LoginResponse loginResponse = userAccessReader.buildLoginResponse(tenant.getId(), user);
        return buildLoginResult(loginResponse, tenantOptions);
    }

    private AuthDto.LoginResult buildTenantSelectResult(LoginSubject subject,
                                                        Boolean rememberMe,
                                                        HttpServletResponse response) {
        List<AuthDto.TenantOption> options = subject.getTenantOptions() == null ? new ArrayList<>() : subject.getTenantOptions();
        if (options.isEmpty()) {
            throw new ForbiddenException("无权限访问");
        }
        LoginTokenStore.LoginTokenPayload payload = new LoginTokenStore.LoginTokenPayload();
        payload.setUserId(subject.getUser().getId());
        payload.setTenantIds(options.stream().map(AuthDto.TenantOption::getTenantId).toList());
        payload.setRememberMe(Boolean.TRUE.equals(rememberMe));
        payload.setLoginMethod(subject.getLoginMethod());
        String loginToken = loginTokenStore.issue(payload);
        authCookieManager.setLoginTokenCookie(response, loginToken, loginTokenStore.ttlSeconds());
        AuthDto.LoginResult result = new AuthDto.LoginResult();
        result.setLoginStage("TENANT_SELECT");
        result.setTenants(options);
        return result;
    }

    private AuthDto.LoginResult buildLoginResult(AuthDto.LoginResponse loginResponse,
                                                 List<AuthDto.TenantOption> tenantOptions) {
        AuthDto.LoginResult result = new AuthDto.LoginResult();
        result.setTenants(tenantOptions);
        result.setUserId(loginResponse.getUserId());
        result.setUsername(loginResponse.getUsername());
        result.setEmail(loginResponse.getEmail());
        result.setRoles(loginResponse.getRoles());
        result.setMenus(loginResponse.getMenus());
        return result;
    }

    private List<AuthDto.TenantOption> resolveTenantOptions(Long userId, Long currentTenantId) {
        List<AuthDto.TenantOption> options = tenantUserMapper.selectTenantOptionsByUserId(userId);
        List<AuthDto.TenantOption> normalized = options == null ? new ArrayList<>() : new ArrayList<>(options);
        int selectedIndex = -1;
        for (int index = 0; index < normalized.size(); index++) {
            AuthDto.TenantOption option = normalized.get(index);
            if (option != null && currentTenantId.equals(option.getTenantId())) {
                selectedIndex = index;
                break;
            }
        }

        if (selectedIndex >= 0) {
            if (selectedIndex > 0) {
                AuthDto.TenantOption selected = normalized.remove(selectedIndex);
                normalized.add(0, selected);
            }
            return normalized;
        }

        Tenant tenant = tenantMapper.selectById(currentTenantId);
        AuthDto.TenantOption fallback = new AuthDto.TenantOption();
        fallback.setTenantId(currentTenantId);
        fallback.setTenantCode(tenant == null ? String.valueOf(currentTenantId) : tenant.getCode());
        fallback.setTenantName(tenant == null ? String.valueOf(currentTenantId) : tenant.getCode());
        fallback.setStatus(tenant == null ? 1 : tenant.getStatus());
        fallback.setIsDefault(1);
        normalized.add(0, fallback);
        return normalized;
    }

    private Map<String, Object> buildAccessClaims(Long tenantId,
                                                  Long userId,
                                                  String sessionId,
                                                  String tokenId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userAccessReader.loadRoleTypes(tenantId, userId));
        claims.put(SessionTokenClaims.SESSION_ID, sessionId);
        claims.put(SessionTokenClaims.TOKEN_ID, tokenId);
        return claims;
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new ForbiddenException("用户已被禁用");
        }
    }

    private void validateTenantStatus(Tenant tenant) {
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new ForbiddenException("租户已被禁用: tenantId=%s", tenant.getId());
        }
    }

    private void validateTenantUser(Long tenantId, Long userId) {
        TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
        if (tenantUser == null) {
            throw new ForbiddenException("无权限访问");
        }
        if (tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
            throw new ForbiddenException("租户成员已被禁用: tenantId=%s,userId=%s", tenantId, userId);
        }
    }
}
