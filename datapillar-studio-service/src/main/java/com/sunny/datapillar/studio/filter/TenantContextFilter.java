package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.GatewayAssertionContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 租户上下文过滤器
 * 负责租户上下文请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_KEY = "tenantId";
    private static final String TENANT_CODE_KEY = "tenantCode";
    private static final String ACTOR_USER_ID_KEY = "actorUserId";
    private static final String ACTOR_TENANT_ID_KEY = "actorTenantId";
    private static final String IMPERSONATION_KEY = "impersonation";

    private static final Set<String> WHITELIST_PREFIX = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/setup",
            "/biz/invitations"
    );

    private final SecurityExceptionHandler securityExceptionHandler;

    public TenantContextFilter(SecurityExceptionHandler securityExceptionHandler) {
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
            if (path.isEmpty()) {
                path = "/";
            }
        }
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
        GatewayAssertionContext assertionContext = GatewayAssertionContext.current(request);
        if (assertionContext == null) {
            securityExceptionHandler.writeError(
                    response, new com.sunny.datapillar.common.exception.UnauthorizedException("gateway_assertion_context_missing"));
            return;
        }

        Long tenantId = assertionContext.tenantId();
        if (tenantId == null) {
            securityExceptionHandler.writeError(
                    response, new com.sunny.datapillar.common.exception.UnauthorizedException("gateway_assertion_tenant_id_missing"));
            return;
        }
        String tenantCode = assertionContext.tenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            securityExceptionHandler.writeError(
                    response, new com.sunny.datapillar.common.exception.UnauthorizedException("gateway_assertion_tenant_code_missing"));
            return;
        }

        boolean impersonation = assertionContext.impersonation();
        Long actorUserId = assertionContext.actorUserId();
        Long actorTenantId = assertionContext.actorTenantId();

        TenantContextHolder.set(new TenantContext(tenantId, tenantCode, actorUserId, actorTenantId, impersonation));
        MDC.put(TENANT_ID_KEY, String.valueOf(tenantId));
        MDC.put(TENANT_CODE_KEY, tenantCode);
        if (actorUserId != null) {
            MDC.put(ACTOR_USER_ID_KEY, String.valueOf(actorUserId));
        }
        if (actorTenantId != null) {
            MDC.put(ACTOR_TENANT_ID_KEY, String.valueOf(actorTenantId));
        }
        MDC.put(IMPERSONATION_KEY, String.valueOf(impersonation));

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(TENANT_ID_KEY);
            MDC.remove(TENANT_CODE_KEY);
            MDC.remove(ACTOR_USER_ID_KEY);
            MDC.remove(ACTOR_TENANT_ID_KEY);
            MDC.remove(IMPERSONATION_KEY);
        }
    }

}
