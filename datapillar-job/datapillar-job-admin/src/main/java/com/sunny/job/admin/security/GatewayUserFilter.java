package com.sunny.job.admin.security;

import com.sunny.common.util.UserContextUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gateway 用户过滤器
 * 从 Gateway 注入的请求头中提取用户信息，设置到请求属性中
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Slf4j
@Component
public class GatewayUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // 排除不需要认证的路径
        if (isExcludedPath(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // 从 Gateway 注入的请求头中提取用户信息
        String userIdStr = request.getHeader(UserContextUtil.HEADER_USER_ID);
        String username = request.getHeader(UserContextUtil.HEADER_USERNAME);

        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                Long userId = Long.parseLong(userIdStr);

                // 将用户信息设置到请求属性中，供后续使用
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);

                log.debug("Gateway 用户信息: userId={}, username={}", userId, username);
            } catch (NumberFormatException e) {
                log.warn("无效的用户ID: {}", userIdStr);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 判断是否为排除的路径（不需要认证）
     */
    private boolean isExcludedPath(String path) {
        // 静态资源路径
        if (path.startsWith("/static/") ||
            path.startsWith("/actuator/") ||
            path.endsWith(".css") ||
            path.endsWith(".js") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".ico")) {
            return true;
        }

        // API 回调路径（执行器回调）- 这些是内部调用，不经过 Gateway
        if (path.startsWith("/openapi/")) {
            return true;
        }

        return false;
    }
}
