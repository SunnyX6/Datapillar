package com.sunny.datapillar.auth.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.JwtTokenUtil;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${cookie.max-age:2592000}")
    private int cookieMaxAge;

    /**
     * 用户登录
     */
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request, HttpServletResponse response) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            log.warn("登录失败: 用户不存在, username={}", request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败: 密码错误, username={}", request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("登录失败: 用户被禁用, username={}", request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }

        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), request.getRememberMe());

        String tokenSign = jwtTokenUtil.extractTokenSignature(accessToken);
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(jwtTokenUtil.getAccessTokenExpiration());
        userMapper.updateTokenSign(user.getId(), tokenSign, expireTime);

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionCodesByUserId(user.getId());
        List<Map<String, Object>> menuMaps = userMapper.selectMenusByUserId(user.getId());
        List<AuthDto.MenuInfo> menus = buildMenuTree(menuMaps);

        // 设置 HttpOnly Cookie
        setAuthCookies(response, accessToken, refreshToken);

        log.info("登录成功: userId={}, username={}", user.getId(), user.getUsername());

        AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
        loginResponse.setUserId(user.getId());
        loginResponse.setUsername(user.getUsername());
        loginResponse.setEmail(user.getEmail());
        loginResponse.setRoles(roles);
        loginResponse.setPermissions(permissions);
        loginResponse.setMenus(menus);

        return loginResponse;
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
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, userId);
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
            }

            String newAccessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());

            String tokenSign = jwtTokenUtil.extractTokenSignature(newAccessToken);
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(jwtTokenUtil.getAccessTokenExpiration());
            userMapper.updateTokenSign(user.getId(), tokenSign, expireTime);

            // 设置新的 access token cookie
            Cookie accessTokenCookie = new Cookie("auth-token", newAccessToken);
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(cookieSecure);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(cookieMaxAge);
            response.addCookie(accessTokenCookie);

            log.info("刷新令牌成功: userId={}, username={}", user.getId(), user.getUsername());

            AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
            loginResponse.setUserId(user.getId());
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
    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        Cookie accessTokenCookie = new Cookie("auth-token", accessToken);
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(cookieSecure);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(cookieMaxAge);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refresh-token", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(cookieSecure);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(cookieMaxAge);
        response.addCookie(refreshTokenCookie);
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
        String username = claims.get("username", String.class);
        String email = claims.get("email", String.class);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, userId);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }

        return AuthDto.TokenResponse.success(userId, username, email);
    }

    /**
     * SSO Token 验证
     */
    public AuthDto.SsoValidateResponse validateSsoToken(AuthDto.SsoValidateRequest request) {
        String token = request.getToken();
        Claims claims = jwtTokenUtil.parseToken(token);

        String tokenType = claims.get("tokenType", String.class);
        if (!"access".equals(tokenType)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_TYPE_ERROR);
        }

        Long userId = Long.parseLong(claims.getSubject());
        String tokenSign = jwtTokenUtil.extractTokenSignature(token);

        User user = userMapper.selectByIdAndTokenSign(userId, tokenSign);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }

        if (user.getTokenExpireTime() != null && user.getTokenExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }

        log.info("SSO 校验成功: userId={}, username={}", user.getId(), user.getUsername());
        return AuthDto.SsoValidateResponse.success(user.getId(), user.getUsername(), user.getEmail());
    }

    /**
     * 登出
     */
    public void logout(String accessToken, HttpServletResponse response) {
        try {
            if (accessToken != null && !accessToken.isBlank()) {
                Long userId = jwtTokenUtil.getUserId(accessToken);
                userMapper.clearTokenSign(userId);
                log.info("用户退出登录: userId={}", userId);
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
                .username(claims.get("username", String.class))
                .build();
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
            menu.setIcon((String) map.get("icon"));
            menu.setPermissionCode((String) map.get("permission_code"));
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
