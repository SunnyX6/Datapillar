package com.sunny.datapillar.workbench.util;

import com.sunny.datapillar.common.constant.HeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类
 * 从 Gateway 注入的请求头中提取用户信息
 */
public final class UserContextUtil {

    private UserContextUtil() {
    }

    /**
     * 获取当前请求
     */
    private static HttpServletRequest getRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * 获取当前用户 ID
     */
    public static Long getUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        String userId = request.getHeader(HeaderConstants.HEADER_USER_ID);
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前用户 ID（必须存在）
     */
    public static Long getRequiredUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new IllegalStateException("用户未登录或用户信息丢失");
        }
        return userId;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader(HeaderConstants.HEADER_USERNAME);
    }

    /**
     * 获取当前租户ID
     */
    public static Long getTenantId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        String tenantId = request.getHeader(HeaderConstants.HEADER_TENANT_ID);
        if (tenantId == null || tenantId.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 是否为平台超管授权访问
     */
    public static boolean isImpersonation() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return false;
        }
        String value = request.getHeader(HeaderConstants.HEADER_IMPERSONATION);
        return "true".equalsIgnoreCase(value);
    }

    /**
     * 获取当前用户邮箱
     */
    public static String getEmail() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        String email = request.getHeader(HeaderConstants.HEADER_EMAIL);
        return (email == null || email.isEmpty()) ? null : email;
    }

    /**
     * 获取链路追踪 ID
     */
    public static String getTraceId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader(HeaderConstants.HEADER_TRACE_ID);
    }

    /**
     * 判断当前是否已登录
     */
    public static boolean isLoggedIn() {
        return getUserId() != null;
    }
}
