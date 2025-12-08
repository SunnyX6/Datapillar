package com.sunny.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import com.sunny.auth.dto.LoginReqDto;
import com.sunny.auth.dto.LoginRespDto;
import com.sunny.auth.dto.OAuth2TokenRespDto;
import com.sunny.auth.dto.SsoValidateReqDto;
import com.sunny.auth.dto.SsoValidateRespDto;
import com.sunny.auth.dto.TokenInfoRespDto;
import com.sunny.auth.dto.TokenReqDto;
import com.sunny.auth.dto.TokenRespDto;
import com.sunny.auth.service.AuthService;
import com.sunny.common.enums.GlobalSystemCode;
import com.sunny.common.exception.GlobalException;
import com.sunny.common.response.ApiResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证控制器
 * 只负责认证相关操作：
 * - 用户登录
 * - 用户登出
 * - Token刷新
 * - Token验证
 * - 健康检查
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.sunny.auth.security.JwtTokenUtil jwtTokenUtil;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${cookie.max-age:604800}")
    private int cookieMaxAge;

    /**
     * 登录接口
     * 设置HttpOnly Cookie来传递accessToken，响应体中不再包含敏感token
     */
    @PostMapping("/login")
    public ApiResponse<LoginRespDto> login(@Valid @RequestBody LoginReqDto request, HttpServletResponse response) {
        try {
            LoginRespDto loginResponse = authService.login(request);

            // 根据 rememberMe 设置不同的 cookie 过期时间
            // rememberMe = true: 30天（与 Refresh Token 一致）
            // rememberMe = false: 30天（统一设置，简化管理）
            int cookieExpiry = cookieMaxAge;  // 统一使用配置的 30 天

            // 设置 accessToken 到 HttpOnly Cookie
            Cookie accessTokenCookie = new Cookie("auth-token", loginResponse.getAccessToken());
            accessTokenCookie.setHttpOnly(true); // 防止XSS攻击
            accessTokenCookie.setSecure(cookieSecure); // HTTPS环境设为true
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(cookieExpiry);
            response.addCookie(accessTokenCookie);

            // 设置 refreshToken 到 HttpOnly Cookie
            Cookie refreshTokenCookie = new Cookie("refresh-token", loginResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(cookieSecure);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(cookieExpiry);
            response.addCookie(refreshTokenCookie);

            // 响应体中移除敏感token，返回用户信息、角色、权限、菜单
            LoginRespDto safeResponse = new LoginRespDto();
            safeResponse.setUserId(loginResponse.getUserId());
            safeResponse.setUsername(loginResponse.getUsername());
            safeResponse.setEmail(loginResponse.getEmail());
            safeResponse.setRoles(loginResponse.getRoles());
            safeResponse.setPermissions(loginResponse.getPermissions());
            safeResponse.setMenus(loginResponse.getMenus());

            return ApiResponse.success(safeResponse);
        } catch (GlobalException e) {
            return ApiResponse.error(e.getGlobalSystemCode());
        } catch (Exception e) {
            return ApiResponse.error(GlobalSystemCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * 刷新 Token 接口
     * 使用 Refresh Token 获取新的 Access Token
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginRespDto> refresh(
            @CookieValue(name = "refresh-token", required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            // 验证 Refresh Token 并生成新的 Access Token
            TokenReqDto request = new TokenReqDto();
            request.setToken(refreshToken);
            LoginRespDto loginResponse = authService.refreshToken(request);

            // 从 Refresh Token 中提取 rememberMe 标志
            Boolean rememberMe = jwtTokenUtil.getRememberMe(refreshToken);

            // 根据 rememberMe 设置 cookie 过期时间
            // 统一使用配置的 30 天（与 Refresh Token 一致）
            int cookieExpiry = cookieMaxAge;  // 30 天

            // 更新 Access Token Cookie
            Cookie accessTokenCookie = new Cookie("auth-token", loginResponse.getAccessToken());
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(cookieSecure);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(cookieExpiry);
            response.addCookie(accessTokenCookie);

            // 响应体中移除敏感 Token,只返回用户信息
            LoginRespDto safeResponse = new LoginRespDto();
            safeResponse.setUserId(loginResponse.getUserId());
            safeResponse.setUsername(loginResponse.getUsername());
            safeResponse.setEmail(loginResponse.getEmail());

            return ApiResponse.success(safeResponse);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), "REFRESH_TOKEN_EXPIRED");
        }
    }

    /**
     * 验证 Token 接口
     */
    @PostMapping("/validate")
    public ApiResponse<TokenRespDto> validate(@Valid @RequestBody TokenReqDto request) {
        TokenRespDto response = authService.validateToken(request);
        return ApiResponse.success(response);
    }

    /**
     * SSO Token 验证接口（供外部系统如XXL-Job、Gravitino调用）
     * POST /api/auth/sso/validate
     */
    @PostMapping("/sso/validate")
    public ApiResponse<SsoValidateRespDto> validateSsoToken(@Valid @RequestBody SsoValidateReqDto request) {
        SsoValidateRespDto response = authService.validateSsoToken(request);
        if (response.getValid()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.error(response.getMessage(), "SSO_VALIDATION_FAILED");
        }
    }

    /**
     * 登出接口
     * 清除认证相关的 Cookie 并撤销数据库中的 Token
     */
    @PostMapping("/logout")
    public ApiResponse<String> logout(@CookieValue(name = "auth-token", required = false) String accessToken,
            HttpServletResponse response) {
        try {
            // 如果有 accessToken，提取 userId 并清空数据库中的 token_sign
            if (accessToken != null && !accessToken.isEmpty()) {
                Long userId = jwtTokenUtil.getUserId(accessToken);
                authService.logout(userId);
            }
        } catch (Exception e) {
            // 即使撤销失败也继续清除 Cookie
        }

        // 清除 auth-token cookie
        Cookie accessTokenCookie = new Cookie("auth-token", "");
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(cookieSecure);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0); // 立即过期
        response.addCookie(accessTokenCookie);

        // 清除 refresh-token cookie
        Cookie refreshTokenCookie = new Cookie("refresh-token", "");
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(cookieSecure);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0); // 立即过期
        response.addCookie(refreshTokenCookie);

        return ApiResponse.success("登出成功");
    }

    /**
     * OAuth2 Token 接口（供Gravitino等外部系统使用）
     * POST /api/auth/oauth/token
     * Content-Type: application/x-www-form-urlencoded
     *
     * 支持标准的 OAuth2 Password Grant Type
     */
    @PostMapping(value = "/oauth/token", consumes = "application/x-www-form-urlencoded")
    public OAuth2TokenRespDto getOAuthToken(
            @RequestParam(name = "grant_type") String grantType,
            @RequestParam(name = "username") String username,
            @RequestParam(name = "password") String password,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret) {

        // 1. 验证 grant_type
        if (!"password".equals(grantType)) {
            throw new IllegalArgumentException("不支持的 grant_type: " + grantType + "，仅支持 password");
        }

        // 2. 复用现有的登录逻辑
        LoginReqDto loginReq = new LoginReqDto();
        loginReq.setUsername(username);
        loginReq.setPassword(password);
        LoginRespDto loginResp = authService.login(loginReq);

        // 3. 返回标准的 OAuth2 Token 响应
        return OAuth2TokenRespDto.builder()
                .accessToken(loginResp.getAccessToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenUtil.getAccessTokenExpiration())
                .refreshToken(loginResp.getRefreshToken())
                .build();
    }

    /**
     * 获取 Token 信息接口
     * 用于前端 Token 管理器查询当前 Token 的剩余时间
     * 不返回 Token 本身，只返回 Token 的元信息
     */
    @GetMapping("/token-info")
    public ApiResponse<TokenInfoRespDto> getTokenInfo(
            @CookieValue(name = "auth-token", required = false) String accessToken) {
        try {
            if (accessToken == null || accessToken.isEmpty()) {
                return ApiResponse.success(TokenInfoRespDto.builder()
                        .valid(false)
                        .remainingSeconds(0L)
                        .build());
            }

            // 解析 Token
            io.jsonwebtoken.Claims claims = jwtTokenUtil.parseToken(accessToken);

            // 计算剩余时间
            long expirationTime = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            long remainingSeconds = Math.max(0, (expirationTime - now) / 1000);

            // 构建响应
            TokenInfoRespDto response = TokenInfoRespDto.builder()
                    .valid(remainingSeconds > 0)
                    .remainingSeconds(remainingSeconds)
                    .expirationTime(expirationTime)
                    .issuedAt(claims.getIssuedAt().getTime())
                    .userId(Long.parseLong(claims.getSubject()))
                    .username(claims.get("username", String.class))
                    .build();

            return ApiResponse.success(response);
        } catch (Exception e) {
            // Token 无效或已过期
            return ApiResponse.success(TokenInfoRespDto.builder()
                    .valid(false)
                    .remainingSeconds(0L)
                    .build());
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("OK");
    }
}
