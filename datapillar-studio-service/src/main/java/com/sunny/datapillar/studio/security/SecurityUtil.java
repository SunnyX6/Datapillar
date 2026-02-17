package com.sunny.datapillar.studio.security;

import com.sunny.datapillar.studio.util.UserContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 安全工具类
 * 提供安全通用工具能力
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@Slf4j
public class SecurityUtil {

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
        return UserContextUtil.getUserId();
    }

    /**
     * 获取当前认证用户信息
     *
     * @return 用户信息，如果未认证或用户不存在则抛出异常
     */
    public String getCurrentUser() {
        return UserContextUtil.getUsername();
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
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getAuthorities() == null) {
                return false;
            }
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ADMIN"::equalsIgnoreCase);
        } catch (Exception e) {
            log.warn("检查用户权限时发生异常", e);
            return false;
        }
    }
}
