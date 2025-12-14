package com.sunny.auth.service;

import com.sunny.auth.dto.*;
import com.sunny.auth.entity.User;
import com.sunny.auth.mapper.UserMapper;
import com.sunny.auth.security.JwtTokenUtil;
import com.sunny.auth.response.AuthErrorCode;
import com.sunny.auth.response.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 用户登录
     */
    public LoginRespDto login(LoginReqDto request) {
        // 1. 根据用户名查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            log.warn("Login failed: user not found, username={}", request.getUsername());
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: incorrect password, username={}", request.getUsername());
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 检查用户状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("Login failed: user disabled, username={}", request.getUsername());
            throw new AuthException(AuthErrorCode.USER_DISABLED);
        }

        // 4. 生成双 Token（将 rememberMe 编码到 Refresh Token 中）
        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), request.getRememberMe());

        // 5. 提取并存储 Token 签名到数据库（用于SSO验证和Token撤销）
        String tokenSign = jwtTokenUtil.extractTokenSignature(accessToken);
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(jwtTokenUtil.getAccessTokenExpiration());
        userMapper.updateTokenSign(user.getId(), tokenSign, expireTime);

        // 6. 查询用户的角色、权限和菜单
        java.util.List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        java.util.List<String> permissions = userMapper.selectPermissionCodesByUserId(user.getId());
        java.util.List<java.util.Map<String, Object>> menuMaps = userMapper.selectMenusByUserId(user.getId());

        // 7. 构建菜单树
        java.util.List<LoginRespDto.MenuDto> menus = buildMenuTree(menuMaps);

        log.info("Login successful: userId={}, username={}, roles={}, permissions={}, menus={}",
                user.getId(), user.getUsername(), roles.size(), permissions.size(), menus.size());

        // 8. 返回完整的登录响应
        LoginRespDto response = new LoginRespDto();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(menus);

        return response;
    }

    /**
     * 刷新 Access Token
     */
    public LoginRespDto refreshToken(TokenReqDto request) {
        try {
            // 1. 验证 Refresh Token
            String refreshToken = request.getRefreshToken();
            if (!jwtTokenUtil.validateToken(refreshToken)) {
                throw new AuthException(AuthErrorCode.TOKEN_EXPIRED);
            }

            // 2. 检查 Token 类型
            String tokenType = jwtTokenUtil.getTokenType(refreshToken);
            if (!"refresh".equals(tokenType)) {
                throw new AuthException(AuthErrorCode.TOKEN_TYPE_ERROR);
            }

            // 3. 提取 userId
            Long userId = jwtTokenUtil.getUserId(refreshToken);

            // 4. 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
            }

            // 5. 检查用户状态
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new AuthException(AuthErrorCode.USER_DISABLED);
            }

            // 6. 生成新的 Access Token (Refresh Token 保持不变)
            String newAccessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUsername(), user.getEmail());

            // 7. 更新数据库中的 Token 签名
            String tokenSign = jwtTokenUtil.extractTokenSignature(newAccessToken);
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(jwtTokenUtil.getAccessTokenExpiration());
            userMapper.updateTokenSign(user.getId(), tokenSign, expireTime);

            log.info("Token refreshed: userId={}, username={}, tokenSign updated", user.getId(), user.getUsername());

            // 刷新 token 只返回基本信息，不重新查询角色权限菜单
            LoginRespDto response = new LoginRespDto();
            response.setAccessToken(newAccessToken);
            response.setRefreshToken(refreshToken);
            response.setUserId(user.getId());
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());

            return response;

        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_FAILED, e.getMessage());
        }
    }

    /**
     * 验证 Token
     */
    public TokenRespDto validateToken(TokenReqDto request) {
        try {
            // 1. 验证 Token 有效性
            String token = request.getToken();
            if (!jwtTokenUtil.validateToken(token)) {
                return TokenRespDto.failure("Token 无效或已过期");
            }

            // 2. 检查 Token 类型 (只接受 Access Token)
            String tokenType = jwtTokenUtil.getTokenType(token);
            if (!"access".equals(tokenType)) {
                return TokenRespDto.failure("Token 类型错误");
            }

            // 3. 提取用户信息
            Long userId = jwtTokenUtil.getUserId(token);
            String username = jwtTokenUtil.getUsername(token);
            String email = jwtTokenUtil.getEmail(token);

            // 4. 验证用户是否存在且状态正常
            User user = userMapper.selectById(userId);
            if (user == null) {
                return TokenRespDto.failure("用户不存在");
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                return TokenRespDto.failure("用户已被禁用");
            }

            return TokenRespDto.success(userId, username, email);

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return TokenRespDto.failure("Token 验证失败: " + e.getMessage());
        }
    }

    /**
     * SSO Token 验证（供外部系统如XXL-Job、Gravitino调用）
     * 验证逻辑：
     * 1. 验证 JWT 签名和过期时间
     * 2. 从数据库查询 token_sign，确保 Token 未被撤销
     * 3. 检查用户状态
     */
    public SsoValidateRespDto validateSsoToken(SsoValidateReqDto request) {
        try {
            // 1. 验证 Token 格式和签名
            String token = request.getToken();
            if (!jwtTokenUtil.validateToken(token)) {
                return SsoValidateRespDto.failure("Token 无效或已过期");
            }

            // 2. 检查 Token 类型（只接受 Access Token）
            String tokenType = jwtTokenUtil.getTokenType(token);
            if (!"access".equals(tokenType)) {
                return SsoValidateRespDto.failure("Token 类型错误，需要 Access Token");
            }

            // 3. 提取用户信息和 Token 签名
            Long userId = jwtTokenUtil.getUserId(token);
            String tokenSign = jwtTokenUtil.extractTokenSignature(token);

            // 4. 从数据库验证 Token 签名（防止Token被撤销）
            User user = userMapper.selectByIdAndTokenSign(userId, tokenSign);
            if (user == null) {
                return SsoValidateRespDto.failure("Token 已被撤销或用户不存在");
            }

            // 5. 检查 Token 是否过期（数据库层面）
            if (user.getTokenExpireTime() != null && user.getTokenExpireTime().isBefore(LocalDateTime.now())) {
                return SsoValidateRespDto.failure("Token 已过期");
            }

            // 6. 检查用户状态
            if (user.getStatus() == null || user.getStatus() != 1) {
                return SsoValidateRespDto.failure("用户已被禁用");
            }

            // 7. 返回成功结果
            log.info("SSO validation successful: userId={}, username={}", user.getId(), user.getUsername());
            return SsoValidateRespDto.success(user.getId(), user.getUsername(), user.getEmail());

        } catch (Exception e) {
            log.error("SSO validation failed: {}", e.getMessage());
            return SsoValidateRespDto.failure("SSO 验证失败: " + e.getMessage());
        }
    }

    /**
     * 登出（撤销 Token）
     */
    public void logout(Long userId) {
        // 清空数据库中的 token_sign，实现即时登出
        userMapper.clearTokenSign(userId);
        log.info("User logged out: userId={}", userId);
    }

    /**
     * 构建菜单树
     */
    private java.util.List<LoginRespDto.MenuDto> buildMenuTree(java.util.List<java.util.Map<String, Object>> menuMaps) {
        if (menuMaps == null || menuMaps.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // 转换为 MenuDto 列表
        java.util.List<LoginRespDto.MenuDto> allMenus = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> map : menuMaps) {
            LoginRespDto.MenuDto menu = new LoginRespDto.MenuDto();
            menu.setId(((Number) map.get("id")).longValue());
            menu.setName((String) map.get("name"));
            menu.setPath((String) map.get("path"));
            menu.setIcon((String) map.get("icon"));
            menu.setPermissionCode((String) map.get("permission_code"));
            menu.setChildren(new java.util.ArrayList<>());
            allMenus.add(menu);
        }

        // 构建菜单树（找出所有根菜单和子菜单）
        java.util.Map<Long, LoginRespDto.MenuDto> menuMap = new java.util.HashMap<>();
        for (LoginRespDto.MenuDto menu : allMenus) {
            menuMap.put(menu.getId(), menu);
        }

        java.util.List<LoginRespDto.MenuDto> rootMenus = new java.util.ArrayList<>();
        for (int i = 0; i < menuMaps.size(); i++) {
            java.util.Map<String, Object> map = menuMaps.get(i);
            LoginRespDto.MenuDto menu = allMenus.get(i);

            Object parentIdObj = map.get("parent_id");
            if (parentIdObj == null) {
                // 根菜单
                rootMenus.add(menu);
            } else {
                // 子菜单，添加到父菜单的 children 中
                Long parentId = ((Number) parentIdObj).longValue();
                LoginRespDto.MenuDto parent = menuMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(menu);
                } else {
                    // 如果父菜单不存在，当作根菜单处理
                    rootMenus.add(menu);
                }
            }
        }

        return rootMenus;
    }
}
