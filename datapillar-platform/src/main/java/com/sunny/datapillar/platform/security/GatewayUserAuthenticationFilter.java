package com.sunny.datapillar.platform.security;

import com.sunny.datapillar.platform.module.user.service.UserService;
import com.sunny.datapillar.common.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Gateway 用户认证过滤器
 * 从 Gateway 注入的请求头中提取用户信息，设置到 SecurityContext
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Slf4j
@Component
public class GatewayUserAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;

    public GatewayUserAuthenticationFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 检查是否已经有认证信息
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // 从 Gateway 注入的请求头中提取用户信息
        String userIdStr = request.getHeader(HeaderConstants.HEADER_USER_ID);
        String username = request.getHeader(HeaderConstants.HEADER_USERNAME);
        String tenantIdStr = request.getHeader(HeaderConstants.HEADER_TENANT_ID);
        String impersonationHeader = request.getHeader(HeaderConstants.HEADER_IMPERSONATION);
        boolean impersonation = "true".equalsIgnoreCase(impersonationHeader);

        if (userIdStr != null && !userIdStr.isEmpty() && username != null && !username.isEmpty()) {
            try {
                Long userId = Long.parseLong(userIdStr);

                List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                if (impersonation) {
                    authorities.add(new SimpleGrantedAuthority("ADMIN"));
                } else {
                    // 查询用户角色和权限
                    List<String> roleCodes = userService.getUserRoleCodes(userId);
                    List<String> permissionCodes = userService.getUserPermissionCodes(userId);

                    roleCodes.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
                    permissionCodes.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
                }

                log.debug("Gateway 认证通过: userId={}, username={}, tenantId={}, impersonation={}",
                        userId, username, tenantIdStr, impersonation);

                // 构建认证对象
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (NumberFormatException e) {
                log.warn("无效的用户ID: {}", userIdStr);
            } catch (Exception e) {
                log.error("设置认证信息失败: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}
