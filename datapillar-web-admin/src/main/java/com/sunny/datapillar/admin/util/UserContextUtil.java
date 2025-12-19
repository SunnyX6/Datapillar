package com.sunny.datapillar.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类
 * 从 Gateway 注入的请求头中提取用户信息
 */
public final class UserContextUtil {

    /**
     * Gateway 注入的请求头常量
     */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_EMAIL = "X-User-Email";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

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
        String userId = request.getHeader(HEADER_USER_ID);
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
        return request.getHeader(HEADER_USERNAME);
    }

    /**
     * 获取当前用户邮箱
     */
    public static String getEmail() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        String email = request.getHeader(HEADER_EMAIL);
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
        return request.getHeader(HEADER_TRACE_ID);
    }

    /**
     * 判断当前是否已登录
     */
    public static boolean isLoggedIn() {
        return getUserId() != null;
    }
}
