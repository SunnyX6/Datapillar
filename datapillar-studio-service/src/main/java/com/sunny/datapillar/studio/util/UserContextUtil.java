package com.sunny.datapillar.studio.util;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.studio.security.GatewayAssertionContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类
 * 提供用户上下文通用工具能力
 *
 * @author Sunny
 * @date 2026-01-01
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
        GatewayAssertionContext context = getAssertionContext();
        return context == null ? null : context.userId();
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
        GatewayAssertionContext context = getAssertionContext();
        return context == null ? null : context.username();
    }

    /**
     * 获取当前租户ID
     */
    public static Long getTenantId() {
        GatewayAssertionContext context = getAssertionContext();
        return context == null ? null : context.tenantId();
    }

    /**
     * 是否为平台超管授权访问
     */
    public static boolean isImpersonation() {
        GatewayAssertionContext context = getAssertionContext();
        return context != null && context.impersonation();
    }

    /**
     * 获取当前用户邮箱
     */
    public static String getEmail() {
        GatewayAssertionContext context = getAssertionContext();
        if (context == null) {
            return null;
        }
        String email = context.email();
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

    private static GatewayAssertionContext getAssertionContext() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return GatewayAssertionContext.current(request);
    }
}
