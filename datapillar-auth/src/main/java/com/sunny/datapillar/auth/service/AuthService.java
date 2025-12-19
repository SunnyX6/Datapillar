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
import com.sunny.datapillar.auth.response.AuthErrorCode;
import com.sunny.datapillar.auth.response.AuthException;
import com.sunny.datapillar.auth.security.JwtTokenUtil;

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
            log.warn("Login failed: user not found, username={}", request.getUsername());
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: incorrect password, username={}", request.getUsername());
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("Login failed: user disabled, username={}", request.getUsername());
            throw new AuthException(AuthErrorCode.USER_DISABLED);
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

        log.info("Login successful: userId={}, username={}", user.getId(), user.getUsername());

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
        try {
            if (!jwtTokenUtil.validateToken(refreshToken)) {
                throw new AuthException(AuthErrorCode.TOKEN_EXPIRED);
            }

            String tokenType = jwtTokenUtil.getTokenType(refreshToken);
            if (!"refresh".equals(tokenType)) {
                throw new AuthException(AuthErrorCode.TOKEN_TYPE_ERROR);
            }

            Long userId = jwtTokenUtil.getUserId(refreshToken);
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new AuthException(AuthErrorCode.USER_DISABLED);
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

            log.info("Token refreshed: userId={}, username={}", user.getId(), user.getUsername());

            AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
            loginResponse.setUserId(user.getId());
            loginResponse.setUsername(user.getUsername());
            loginResponse.setEmail(user.getEmail());

            return loginResponse;

        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_FAILED, e.getMessage());
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
        try {
            String token = request.getToken();
            if (!jwtTokenUtil.validateToken(token)) {
                return AuthDto.TokenResponse.failure("Token 无效或已过期");
            }

            String tokenType = jwtTokenUtil.getTokenType(token);
            if (!"access".equals(tokenType)) {
                return AuthDto.TokenResponse.failure("Token 类型错误");
            }

            Long userId = jwtTokenUtil.getUserId(token);
            String username = jwtTokenUtil.getUsername(token);
            String email = jwtTokenUtil.getEmail(token);

            User user = userMapper.selectById(userId);
            if (user == null) {
                return AuthDto.TokenResponse.failure("用户不存在");
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                return AuthDto.TokenResponse.failure("用户已被禁用");
            }

            return AuthDto.TokenResponse.success(userId, username, email);

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return AuthDto.TokenResponse.failure("Token 验证失败: " + e.getMessage());
        }
    }

    /**
     * SSO Token 验证
     */
    public AuthDto.SsoValidateResponse validateSsoToken(AuthDto.SsoValidateRequest request) {
        try {
            String token = request.getToken();
            if (!jwtTokenUtil.validateToken(token)) {
                return AuthDto.SsoValidateResponse.failure("Token 无效或已过期");
            }

            String tokenType = jwtTokenUtil.getTokenType(token);
            if (!"access".equals(tokenType)) {
                return AuthDto.SsoValidateResponse.failure("Token 类型错误，需要 Access Token");
            }

            Long userId = jwtTokenUtil.getUserId(token);
            String tokenSign = jwtTokenUtil.extractTokenSignature(token);

            User user = userMapper.selectByIdAndTokenSign(userId, tokenSign);
            if (user == null) {
                return AuthDto.SsoValidateResponse.failure("Token 已被撤销或用户不存在");
            }

            if (user.getTokenExpireTime() != null && user.getTokenExpireTime().isBefore(LocalDateTime.now())) {
                return AuthDto.SsoValidateResponse.failure("Token 已过期");
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                return AuthDto.SsoValidateResponse.failure("用户已被禁用");
            }

            log.info("SSO validation successful: userId={}, username={}", user.getId(), user.getUsername());
            return AuthDto.SsoValidateResponse.success(user.getId(), user.getUsername(), user.getEmail());

        } catch (Exception e) {
            log.error("SSO validation failed: {}", e.getMessage());
            return AuthDto.SsoValidateResponse.failure("SSO 验证失败: " + e.getMessage());
        }
    }

    /**
     * 登出
     */
    public void logout(Long userId) {
        userMapper.clearTokenSign(userId);
        log.info("User logged out: userId={}", userId);
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
