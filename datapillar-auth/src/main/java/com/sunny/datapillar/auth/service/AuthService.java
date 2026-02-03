package com.sunny.datapillar.auth.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.entity.UserIdentity;
import com.sunny.datapillar.auth.entity.UserInvitation;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.mapper.UserIdentityMapper;
import com.sunny.datapillar.auth.mapper.UserInvitationMapper;
import com.sunny.datapillar.auth.security.JwtTokenUtil;
import com.sunny.datapillar.auth.sso.SsoAuthService;
import com.sunny.datapillar.auth.sso.SsoQrService;
import com.sunny.datapillar.auth.sso.model.SsoQrResponse;
import com.sunny.datapillar.auth.sso.model.SsoUserInfo;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证服务
 *
 * @author sunny
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantUserMapper tenantUserMapper;
    private final UserInvitationMapper userInvitationMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final SsoAuthService ssoAuthService;
    private final SsoQrService ssoQrService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * 用户登录
     */
    @Transactional
    public AuthDto.LoginResult login(AuthDto.LoginRequest request, HttpServletResponse response) {
        String tenantCode = normalizeTenantCode(request.getTenantCode());
        User user = userMapper.selectByUsername(request.getUsername());

        if (user != null) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("登录失败: 密码错误, username={}", request.getUsername());
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }
            validateUserStatus(user);
        }

        if (tenantCode == null) {
            if (user == null) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }
            AuthDto.LoginResult result = loginWithoutTenant(user, request.getRememberMe(), response);
            if ("TENANT_SELECT".equals(result.getLoginStage())) {
                log.info("登录成功(待选租户): userId={}, username={}", user.getId(), user.getUsername());
            } else {
                log.info("登录成功: tenantId={}, userId={}, username={}",
                        result.getTenantId(), user.getId(), user.getUsername());
            }
            return result;
        }

        Tenant tenant = loadTenant(tenantCode);
        TenantUser tenantUser = null;
        if (user == null) {
            UserInvitation invitation = loadValidInvitation(request.getInviteCode(), tenant.getId());
            validateInviteMatch(invitation, request.getEmail(), request.getPhone());
            user = createLocalUser(tenant.getId(), request);
            applyInvitation(tenant, user, invitation);
        } else {
            tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenant.getId(), user.getId());
            if (tenantUser == null) {
                UserInvitation invitation = loadValidInvitation(request.getInviteCode(), tenant.getId());
                String matchEmail = pickFirstNotBlank(user.getEmail(), request.getEmail());
                String matchPhone = pickFirstNotBlank(user.getPhone(), request.getPhone());
                validateInviteMatch(invitation, matchEmail, matchPhone);
                updateUserContactIfBlank(user, request.getEmail(), request.getPhone());
                applyInvitation(tenant, user, invitation);
            } else {
                validateTenantUserStatus(tenantUser, tenant.getId(), user.getId());
            }
        }

        AuthDto.LoginResult loginResult = loginForTenant(user, tenant.getId(), request.getRememberMe(), response);
        log.info("登录成功: tenantId={}, userId={}, username={}", tenant.getId(), user.getId(), user.getUsername());
        return loginResult;
    }

    /**
     * 选择租户后完成登录
     */
    @Transactional
    public AuthDto.LoginResult loginWithTenant(AuthDto.LoginTenantRequest request, HttpServletResponse response) {
        if (request == null || request.getLoginToken() == null || request.getLoginToken().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        Claims claims = jwtTokenUtil.parseToken(request.getLoginToken());
        String tokenType = claims.get("tokenType", String.class);
        if (!"login".equals(tokenType)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_TYPE_ERROR);
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, userId);
        }
        validateUserStatus(user);

        Long tenantId = request.getTenantId();
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_NOT_FOUND, String.valueOf(tenantId));
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_DISABLED, tenantId);
        }

        Boolean rememberMe = claims.get("rememberMe", Boolean.class);
        AuthDto.LoginResult loginResult = loginForTenant(user, tenantId, rememberMe, response);
        log.info("租户选择登录成功: tenantId={}, userId={}, username={}", tenantId, userId, user.getUsername());
        return loginResult;
    }

    /**
     * SSO 扫码配置
     */
    public AuthDto.SsoQrResponse getSsoQr(String tenantCode, String provider) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_ARGUMENT);
        }
        String normalizedProvider = normalizeProvider(provider);
        if (normalizedProvider == null || normalizedProvider.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_ARGUMENT);
        }
        Tenant tenant = loadTenant(tenantCode);
        SsoQrResponse qrResponse = ssoQrService.buildQr(tenant.getId(), normalizedProvider);
        return new AuthDto.SsoQrResponse(qrResponse.getType(), qrResponse.getState(), qrResponse.getPayload());
    }

    /**
     * 刷新 Access Token
     */
    public AuthDto.LoginResponse refreshToken(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
        }

        try {
            if (!jwtTokenUtil.validateToken(refreshToken)) {
                throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
            }

            String tokenType;
            try {
                tokenType = jwtTokenUtil.getTokenType(refreshToken);
            } catch (BusinessException e) {
                throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
            }

            if (!"refresh".equals(tokenType)) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_TYPE_ERROR);
            }

            Long userId = jwtTokenUtil.getUserId(refreshToken);
            Long tenantId = jwtTokenUtil.getTenantId(refreshToken);
            if (tenantId == null) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
            }

            Tenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null) {
                throw new BusinessException(ErrorCode.AUTH_TENANT_NOT_FOUND, String.valueOf(tenantId));
            }
            if (tenant.getStatus() == null || tenant.getStatus() != 1) {
                throw new BusinessException(ErrorCode.AUTH_TENANT_DISABLED, tenantId);
            }

            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, userId);
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
            }

            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
            if (tenantUser == null) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
            }
            validateTenantUserStatus(tenantUser, tenantId, userId);

            String newAccessToken = jwtTokenUtil.generateAccessToken(user.getId(), tenantId, user.getUsername(), user.getEmail());
            updateTenantUserToken(tenantId, userId, newAccessToken);

            // 设置新的 access token cookie
            setAccessTokenCookie(response, newAccessToken);

            log.info("刷新令牌成功: tenantId={}, userId={}, username={}", tenantId, user.getId(), user.getUsername());

            AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
            loginResponse.setUserId(user.getId());
            loginResponse.setTenantId(tenantId);
            loginResponse.setUsername(user.getUsername());
            loginResponse.setEmail(user.getEmail());

            return loginResponse;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("刷新令牌失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_FAILED, e.getMessage());
        }
    }

    /**
     * 设置认证 Cookie
     */
    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken, Boolean rememberMe) {
        int accessMaxAge = Math.toIntExact(jwtTokenUtil.getAccessTokenExpiration());
        int refreshMaxAge = Math.toIntExact(jwtTokenUtil.getRefreshTokenExpiration(Boolean.TRUE.equals(rememberMe)));

        Cookie accessTokenCookie = new Cookie("auth-token", accessToken);
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(cookieSecure);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(accessMaxAge);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refresh-token", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(cookieSecure);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(refreshMaxAge);
        response.addCookie(refreshTokenCookie);
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        int accessMaxAge = Math.toIntExact(jwtTokenUtil.getAccessTokenExpiration());
        Cookie accessTokenCookie = new Cookie("auth-token", accessToken);
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(cookieSecure);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(accessMaxAge);
        response.addCookie(accessTokenCookie);
    }

    /**
     * 清除认证 Cookie
     */
    public void clearAuthCookies(HttpServletResponse response) {
        Cookie accessTokenCookie = new Cookie("auth-token", "");
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(cookieSecure);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refresh-token", "");
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(cookieSecure);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);
    }

    /**
     * 验证 Token
     */
    public AuthDto.TokenResponse validateToken(AuthDto.TokenRequest request) {
        String token = request.getToken();
        Claims claims = jwtTokenUtil.parseToken(token);

        String tokenType = claims.get("tokenType", String.class);
        if (!"access".equals(tokenType)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_TYPE_ERROR);
        }

        Long userId = Long.parseLong(claims.getSubject());
        Long tenantId = jwtTokenUtil.getTenantId(token);
        String username = claims.get("username", String.class);
        String email = claims.get("email", String.class);

        if (tenantId == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_NOT_FOUND, String.valueOf(tenantId));
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_DISABLED, tenantId);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, userId);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }

        boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
        if (!impersonation) {
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
            if (tenantUser == null) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
            }
            validateTenantUserStatus(tenantUser, tenantId, userId);

            String tokenSign = jwtTokenUtil.extractTokenSignature(token);
            if (tenantUser.getTokenSign() == null || !tenantUser.getTokenSign().equals(tokenSign)) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
            }
            if (tenantUser.getTokenExpireTime() != null && tenantUser.getTokenExpireTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
            }
        }

        return AuthDto.TokenResponse.success(userId, tenantId, username, email);
    }

    /**
     * 平台超管授权访问目标租户（assume），签发用于目标租户业务接口访问的 access token。
     */
    public AuthDto.LoginResponse assumeTenant(Long tenantId, String accessToken, HttpServletResponse response) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        Claims claims = jwtTokenUtil.parseToken(accessToken);
        String tokenType = claims.get("tokenType", String.class);
        if (!"access".equals(tokenType)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_TYPE_ERROR);
        }

        Long actorUserId = Long.parseLong(claims.getSubject());
        Long actorTenantId = jwtTokenUtil.getTenantId(accessToken);
        if (actorTenantId == null || actorTenantId != 0L) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        User actor = userMapper.selectById(actorUserId);
        if (actor == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, actorUserId);
        }
        validateUserStatus(actor);

        List<AuthDto.RoleInfo> systemRoles = userMapper.selectRolesByUserId(0L, actorUserId);
        boolean isAdmin = systemRoles != null && systemRoles.stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getType()));
        if (!isAdmin) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        Tenant targetTenant = tenantMapper.selectById(tenantId);
        if (targetTenant == null) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_NOT_FOUND, String.valueOf(tenantId));
        }
        if (targetTenant.getStatus() == null || targetTenant.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_DISABLED, tenantId);
        }

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("actorUserId", actorUserId);
        extraClaims.put("actorTenantId", 0L);
        extraClaims.put("impersonation", true);

        String newAccessToken = jwtTokenUtil.generateAccessToken(
                actorUserId, tenantId, actor.getUsername(), actor.getEmail(), extraClaims);
        setAccessTokenCookie(response, newAccessToken);

        return buildAssumeLoginResponse(tenantId, actor);
    }

    /**
     * SSO 登录
     */
    @Transactional
    public AuthDto.LoginResult loginWithSso(AuthDto.SsoLoginRequest request, HttpServletResponse response) {
        Tenant tenant = loadTenant(request.getTenantCode());
        String provider = normalizeProvider(request.getProvider());
        if (provider == null || provider.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_ARGUMENT);
        }

        SsoUserInfo userInfo = ssoAuthService.authenticate(tenant.getId(), provider, request.getAuthCode(), request.getState());
        String externalUserId = userInfo.getExternalUserId();
        String username = userInfo.getNick();
        String email = userInfo.getEmail();
        String mobile = userInfo.getMobile();

        UserIdentity identity = userIdentityMapper.selectByProviderAndExternalUserId(tenant.getId(), provider, externalUserId);
        User user = null;
        if (identity != null) {
            user = userMapper.selectById(identity.getUserId());
            if (user == null) {
                throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, identity.getUserId());
            }
        } else if (email != null && !email.isBlank()) {
            user = userMapper.selectByEmail(email);
        }

        if (user != null) {
            validateUserStatus(user);
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenant.getId(), user.getId());
            if (tenantUser == null) {
                UserInvitation invitation = loadValidInvitation(request.getInviteCode(), tenant.getId());
                validateInviteMatch(invitation, email, mobile);
                updateUserContactIfBlank(user, email, mobile);
                applyInvitation(tenant, user, invitation);
            } else {
                validateTenantUserStatus(tenantUser, tenant.getId(), user.getId());
            }
            if (identity == null) {
                createUserIdentity(tenant.getId(), user.getId(), provider, userInfo);
            }
        } else {
            UserInvitation invitation = loadValidInvitation(request.getInviteCode(), tenant.getId());
            validateInviteMatch(invitation, email, mobile);
            String finalUsername = buildSsoUsername(username, email, externalUserId);
            user = createSsoUser(tenant.getId(), finalUsername, email, mobile);
            applyInvitation(tenant, user, invitation);
            createUserIdentity(tenant.getId(), user.getId(), provider, userInfo);
        }

        List<AuthDto.TenantOption> tenantOptions = loadTenantOptions(user.getId());
        if (tenantOptions.size() > 1) {
            String loginToken = jwtTokenUtil.generateLoginToken(user.getId(), false);
            AuthDto.LoginResult selectResult = buildTenantSelectResult(loginToken, tenantOptions);
            log.info("SSO 登录成功(待选租户): userId={}, username={}", user.getId(), user.getUsername());
            return selectResult;
        }

        AuthDto.LoginResult loginResult = loginForTenant(user, tenant.getId(), false, response);
        log.info("SSO 登录成功: tenantId={}, userId={}, username={}", tenant.getId(), user.getId(), user.getUsername());
        return loginResult;
    }

    /**
     * 登出
     */
    public void logout(String accessToken, HttpServletResponse response) {
        try {
            if (accessToken != null && !accessToken.isBlank()) {
                Claims claims = jwtTokenUtil.parseToken(accessToken);
                Long userId = Long.parseLong(claims.getSubject());
                Long tenantId = jwtTokenUtil.getTenantId(accessToken);
                boolean impersonation = Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
                if (!impersonation && tenantId != null) {
                    tenantUserMapper.clearTokenSign(tenantId, userId);
                }
                log.info("用户退出登录: tenantId={}, userId={}, impersonation={}", tenantId, userId, impersonation);
            }
        } finally {
            clearAuthCookies(response);
        }
    }

    public AuthDto.TokenInfo getTokenInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        Claims claims = jwtTokenUtil.parseToken(accessToken);

        long expirationTime = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        long remainingSeconds = Math.max(0, (expirationTime - now) / 1000);
        if (remainingSeconds <= 0) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        return AuthDto.TokenInfo.builder()
                .valid(true)
                .remainingSeconds(remainingSeconds)
                .expirationTime(expirationTime)
                .issuedAt(claims.getIssuedAt().getTime())
                .userId(Long.parseLong(claims.getSubject()))
                .tenantId(jwtTokenUtil.getTenantId(accessToken))
                .username(claims.get("username", String.class))
                .build();
    }

    private Tenant loadTenant(String tenantCode) {
        Tenant tenant = tenantMapper.selectByCode(tenantCode);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_NOT_FOUND, tenantCode);
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_DISABLED, tenant.getId());
        }
        return tenant;
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }
    }

    private void validateTenantUserStatus(TenantUser tenantUser, Long tenantId, Long userId) {
        if (tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_TENANT_USER_DISABLED, tenantId, userId);
        }
    }

    private UserInvitation loadValidInvitation(String inviteCode, Long tenantId) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVITE_REQUIRED);
        }
        UserInvitation invitation = userInvitationMapper.selectByInviteCode(inviteCode);
        if (invitation == null || invitation.getTenantId() == null || !invitation.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.AUTH_INVITE_INVALID);
        }
        if (invitation.getStatus() != null && invitation.getStatus() != 0) {
            if (invitation.getStatus() == 1) {
                throw new BusinessException(ErrorCode.AUTH_INVITE_ALREADY_USED);
            }
            if (invitation.getStatus() == 2) {
                throw new BusinessException(ErrorCode.AUTH_INVITE_EXPIRED);
            }
            throw new BusinessException(ErrorCode.AUTH_INVITE_INVALID);
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_INVITE_EXPIRED);
        }
        return invitation;
    }

    private void validateInviteMatch(UserInvitation invitation, String email, String mobile) {
        String inviteEmail = normalizeEmail(invitation.getInviteeEmail());
        if (inviteEmail != null && !inviteEmail.isBlank()) {
            String inputEmail = normalizeEmail(email);
            if (inputEmail == null || !inviteEmail.equals(inputEmail)) {
                throw new BusinessException(ErrorCode.AUTH_INVITE_MISMATCH);
            }
        }
        String inviteMobile = normalizePhone(invitation.getInviteeMobile());
        if (inviteMobile != null && !inviteMobile.isBlank()) {
            String inputMobile = normalizePhone(mobile);
            if (inputMobile == null || !inviteMobile.equals(inputMobile)) {
                throw new BusinessException(ErrorCode.AUTH_INVITE_MISMATCH);
            }
        }
    }

    private void applyInvitation(Tenant tenant, User user, UserInvitation invitation) {
        insertTenantUser(tenant.getId(), user.getId());

        int updated = userInvitationMapper.acceptInvitation(invitation.getId(), user.getId(), LocalDateTime.now(), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.AUTH_INVITE_ALREADY_USED);
        }
    }

    private void insertTenantUser(Long tenantId, Long userId) {
        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(tenantId);
        tenantUser.setUserId(userId);
        tenantUser.setStatus(1);
        tenantUser.setIsDefault(tenantUserMapper.countByUserId(userId) == 0 ? 1 : 0);
        tenantUser.setJoinedAt(LocalDateTime.now());
        tenantUserMapper.insert(tenantUser);
    }

    private void updateTenantUserToken(Long tenantId, Long userId, String accessToken) {
        String tokenSign = jwtTokenUtil.extractTokenSignature(accessToken);
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(jwtTokenUtil.getAccessTokenExpiration());
        int updated = tenantUserMapper.updateTokenSign(tenantId, userId, tokenSign, expireTime);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
    }

    private AuthDto.LoginResponse buildLoginResponse(Long tenantId, User user) {
        List<AuthDto.RoleInfo> roles = userMapper.selectRolesByUserId(tenantId, user.getId());
        List<Map<String, Object>> menuMaps = userMapper.selectMenusByUserId(tenantId, user.getId());
        List<AuthDto.MenuInfo> menus = buildMenuTree(menuMaps);

        AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
        loginResponse.setUserId(user.getId());
        loginResponse.setTenantId(tenantId);
        loginResponse.setUsername(user.getUsername());
        loginResponse.setEmail(user.getEmail());
        loginResponse.setRoles(roles);
        loginResponse.setMenus(menus);
        return loginResponse;
    }

    private AuthDto.LoginResponse buildAssumeLoginResponse(Long tenantId, User user) {
        List<AuthDto.RoleInfo> roles = new ArrayList<>();
        roles.add(new AuthDto.RoleInfo(0L, "平台管理员", "ADMIN"));

        List<Map<String, Object>> menuMaps = userMapper.selectMenusByTenantId(tenantId);
        List<AuthDto.MenuInfo> menus = buildMenuTree(menuMaps);

        AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
        loginResponse.setUserId(user.getId());
        loginResponse.setTenantId(tenantId);
        loginResponse.setUsername(user.getUsername());
        loginResponse.setEmail(user.getEmail());
        loginResponse.setRoles(roles);
        loginResponse.setMenus(menus);
        return loginResponse;
    }

    private AuthDto.LoginResult loginWithoutTenant(User user, Boolean rememberMe, HttpServletResponse response) {
        List<AuthDto.TenantOption> tenantOptions = loadTenantOptions(user.getId());
        if (tenantOptions.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (tenantOptions.size() == 1) {
            return loginForTenant(user, tenantOptions.get(0).getTenantId(), rememberMe, response);
        }
        String loginToken = jwtTokenUtil.generateLoginToken(user.getId(), rememberMe);
        return buildTenantSelectResult(loginToken, tenantOptions);
    }

    private AuthDto.LoginResult loginForTenant(User user, Long tenantId, Boolean rememberMe, HttpServletResponse response) {
        TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, user.getId());
        if (tenantUser == null) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        validateTenantUserStatus(tenantUser, tenantId, user.getId());

        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), tenantId, user.getUsername(), user.getEmail());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), tenantId, rememberMe);
        updateTenantUserToken(tenantId, user.getId(), accessToken);

        AuthDto.LoginResponse loginResponse = buildLoginResponse(tenantId, user);
        setAuthCookies(response, accessToken, refreshToken, rememberMe);
        return buildLoginResult(loginResponse);
    }

    private List<AuthDto.TenantOption> loadTenantOptions(Long userId) {
        List<AuthDto.TenantOption> options = tenantUserMapper.selectTenantOptionsByUserId(userId);
        return options == null ? new ArrayList<>() : options;
    }

    private AuthDto.LoginResult buildLoginResult(AuthDto.LoginResponse loginResponse) {
        AuthDto.LoginResult result = new AuthDto.LoginResult();
        result.setLoginStage("SUCCESS");
        result.setUserId(loginResponse.getUserId());
        result.setTenantId(loginResponse.getTenantId());
        result.setUsername(loginResponse.getUsername());
        result.setEmail(loginResponse.getEmail());
        result.setRoles(loginResponse.getRoles());
        result.setMenus(loginResponse.getMenus());
        return result;
    }

    private AuthDto.LoginResult buildTenantSelectResult(String loginToken, List<AuthDto.TenantOption> tenants) {
        AuthDto.LoginResult result = new AuthDto.LoginResult();
        result.setLoginStage("TENANT_SELECT");
        result.setLoginToken(loginToken);
        result.setTenants(tenants);
        return result;
    }

    private User createLocalUser(Long tenantId, AuthDto.LoginRequest request) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private User createSsoUser(Long tenantId, String username, String email, String mobile) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("sso_" + UUID.randomUUID()));
        user.setEmail(email);
        user.setPhone(mobile);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private String buildSsoUsername(String username, String email, String externalUserId) {
        String candidate = pickFirstNotBlank(username, email);
        if (candidate == null || candidate.isBlank()) {
            candidate = "sso_" + externalUserId;
        }
        User existing = userMapper.selectByUsername(candidate);
        if (existing != null) {
            String fallback = "sso_" + externalUserId;
            if (userMapper.selectByUsername(fallback) != null) {
                throw new BusinessException(ErrorCode.AUTH_DUPLICATE_KEY);
            }
            return fallback;
        }
        return candidate;
    }

    private void updateUserContactIfBlank(User user, String email, String phone) {
        boolean changed = false;
        if ((user.getEmail() == null || user.getEmail().isBlank()) && email != null && !email.isBlank()) {
            user.setEmail(email);
            changed = true;
        }
        if ((user.getPhone() == null || user.getPhone().isBlank()) && phone != null && !phone.isBlank()) {
            user.setPhone(phone);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(user);
        }
    }

    private void createUserIdentity(Long tenantId, Long userId, String provider, SsoUserInfo userInfo) {
        UserIdentity identity = new UserIdentity();
        identity.setTenantId(tenantId);
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setExternalUserId(userInfo.getExternalUserId());
        identity.setUnionId(userInfo.getUnionId());
        identity.setOpenId(userInfo.getOpenId());
        identity.setEmail(userInfo.getEmail());
        identity.setMobile(userInfo.getMobile());
        identity.setProfileJson(userInfo.getRawJson());
        identity.setStatus(1);
        userIdentityMapper.insert(identity);
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        return tenantCode.trim();
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String pickFirstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.trim();
    }

    /**
     * 构建菜单树
     */
    private List<AuthDto.MenuInfo> buildMenuTree(List<Map<String, Object>> menuMaps) {
        if (menuMaps == null || menuMaps.isEmpty()) {
            return new ArrayList<>();
        }

        List<AuthDto.MenuInfo> allMenus = new ArrayList<>();
        for (Map<String, Object> map : menuMaps) {
            AuthDto.MenuInfo menu = new AuthDto.MenuInfo();
            menu.setId(((Number) map.get("id")).longValue());
            menu.setName((String) map.get("name"));
            menu.setPath((String) map.get("path"));
            menu.setPermissionCode((String) map.get("permission_code"));
            menu.setLocation((String) map.get("location"));
            Object categoryId = map.get("category_id");
            if (categoryId instanceof Number) {
                menu.setCategoryId(((Number) categoryId).longValue());
            }
            menu.setCategoryName((String) map.get("category_name"));
            menu.setChildren(new ArrayList<>());
            allMenus.add(menu);
        }

        Map<Long, AuthDto.MenuInfo> menuMap = new HashMap<>();
        for (AuthDto.MenuInfo menu : allMenus) {
            menuMap.put(menu.getId(), menu);
        }

        List<AuthDto.MenuInfo> rootMenus = new ArrayList<>();
        for (int i = 0; i < menuMaps.size(); i++) {
            Map<String, Object> map = menuMaps.get(i);
            AuthDto.MenuInfo menu = allMenus.get(i);

            Object parentIdObj = map.get("parent_id");
            if (parentIdObj == null) {
                rootMenus.add(menu);
            } else {
                Long parentId = ((Number) parentIdObj).longValue();
                AuthDto.MenuInfo parent = menuMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(menu);
                } else {
                    rootMenus.add(menu);
                }
            }
        }

        return rootMenus;
    }
}
