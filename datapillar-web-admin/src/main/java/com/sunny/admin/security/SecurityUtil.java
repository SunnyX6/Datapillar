package com.sunny.admin.security;

import com.sunny.common.enums.GlobalSystemCode;
import com.sunny.common.exception.GlobalException;
import com.sunny.admin.module.user.entity.User;
import com.sunny.admin.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全工具类
 * 用于获取当前认证用户信息
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Component
@Slf4j
public class SecurityUtil {

    @Autowired
    private UserService userService;

    /**
     * 获取当前认证用户的用户名
     * 
     * @return 用户名，如果未认证则返回null
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return null;
    }

    /**
     * 获取当前认证用户的用户ID
     *
     * @return 用户ID，如果未认证或用户不存在则抛出异常
     */
    public Long getCurrentUserId() {
        String username = getCurrentUsername();
        if (username == null) {
            throw new GlobalException(GlobalSystemCode.USER_NOT_LOGGED_IN);
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            throw new GlobalException(GlobalSystemCode.USER_NOT_FOUND, username);
        }

        return user.getId();
    }

    /**
     * 获取当前认证用户信息
     *
     * @return 用户信息，如果未认证或用户不存在则抛出异常
     */
    public User getCurrentUser() {
        String username = getCurrentUsername();
        if (username == null) {
            throw new GlobalException(GlobalSystemCode.USER_NOT_LOGGED_IN);
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            throw new GlobalException(GlobalSystemCode.USER_NOT_FOUND, username);
        }

        return user;
    }

    /**
     * 检查当前用户是否已认证
     * 
     * @return 是否已认证
     */
    public boolean isAuthenticated() {
        return getCurrentUsername() != null;
    }

    /**
     * 检查当前用户是否为管理员
     * 
     * @return 是否为管理员
     */
    public boolean isCurrentUserAdmin() {
        try {
            Long currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                return false;
            }
            List<String> roleCodes = userService.getUserRoleCodes(currentUserId);
            return roleCodes != null && roleCodes.contains("ADMIN");
        } catch (Exception e) {
            log.warn("检查用户权限时发生异常", e);
            return false;
        }
    }
}