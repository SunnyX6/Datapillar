package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.GatewayAssertionContext;
import com.sunny.datapillar.studio.security.GatewayAssertionProperties;
import com.sunny.datapillar.studio.security.GatewayAssertionVerifier;
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
import java.util.Set;

/**
 * 网关断言过滤器
 * 负责网关断言请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class GatewayAssertionFilter extends OncePerRequestFilter {

    private static final Set<String> WHITELIST_PREFIX = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/setup",
            "/biz/invitations"
    );

    private final GatewayAssertionProperties properties;
    private final GatewayAssertionVerifier verifier;
    private final SecurityExceptionHandler securityExceptionHandler;

    public GatewayAssertionFilter(GatewayAssertionProperties properties,
                                  GatewayAssertionVerifier verifier,
                                  SecurityExceptionHandler securityExceptionHandler) {
        this.properties = properties;
        this.verifier = verifier;
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = normalizedPathForWhitelist(request);
        for (String prefix : WHITELIST_PREFIX) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String assertion = request.getHeader(properties.getHeaderName());
        if (assertion == null || assertion.isBlank()) {
            securityExceptionHandler.writeError(
                    response, new com.sunny.datapillar.common.exception.UnauthorizedException("gateway_assertion_header_missing"));
            return;
        }

        String method = request.getMethod();
        String path = normalizedPath(request);

        try {
            GatewayAssertionContext context = verifier.verify(assertion, method, path);
            if (context.tokenId() == null || context.tokenId().isBlank()) {
                securityExceptionHandler.writeError(
                        response, new com.sunny.datapillar.common.exception.UnauthorizedException("gateway_assertion_token_id_missing"));
                return;
            }

            GatewayAssertionContext.attach(request, context);

            List<SimpleGrantedAuthority> authorities = context.roles().stream()
                    .map(role -> role == null ? "" : role.trim())
                    .filter(role -> !role.isEmpty())
                    .map(role -> new SimpleGrantedAuthority(role.toUpperCase()))
                    .toList();

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    context.username(),
                    null,
                    authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            chain.doFilter(request, response);
        } catch (Exception ex) {
            log.warn("security_event event=gateway_assertion_verify_failed path={} method={} reason={}",
                    path, method, ex.getMessage());
            securityExceptionHandler.writeError(response, new com.sunny.datapillar.common.exception.UnauthorizedException(ex, "未授权访问"));
        }
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private String normalizedPathForWhitelist(HttpServletRequest request) {
        String path = normalizedPath(request);
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
            if (path.isEmpty()) {
                return "/";
            }
        }
        return path;
    }
}
