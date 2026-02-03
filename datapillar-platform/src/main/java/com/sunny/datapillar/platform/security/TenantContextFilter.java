package com.sunny.datapillar.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.platform.context.TenantContext;
import com.sunny.datapillar.platform.context.TenantContextHolder;
import com.sunny.datapillar.platform.web.security.SecurityErrorWriter;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 租户上下文过滤器
 */
@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_KEY = "tenantId";
    private static final String ACTOR_USER_ID_KEY = "actorUserId";
    private static final String ACTOR_TENANT_ID_KEY = "actorTenantId";
    private static final String IMPERSONATION_KEY = "impersonation";

    private static final Set<String> WHITELIST_PREFIX = Set.of(
            "/health",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final ObjectMapper objectMapper;

    public TenantContextFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
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
        String tenantIdHeader = request.getHeader(HeaderConstants.HEADER_TENANT_ID);
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            SecurityErrorWriter.writeError(request, response, ErrorCode.ADMIN_UNAUTHORIZED, objectMapper);
            return;
        }

        Long tenantId;
        try {
            tenantId = Long.parseLong(tenantIdHeader);
        } catch (NumberFormatException e) {
            SecurityErrorWriter.writeError(request, response, ErrorCode.ADMIN_UNAUTHORIZED, objectMapper);
            return;
        }

        boolean impersonation = "true".equalsIgnoreCase(request.getHeader(HeaderConstants.HEADER_IMPERSONATION));
        Long actorUserId = parseLongHeader(request.getHeader(HeaderConstants.HEADER_ACTOR_USER_ID));
        Long actorTenantId = parseLongHeader(request.getHeader(HeaderConstants.HEADER_ACTOR_TENANT_ID));

        TenantContextHolder.set(new TenantContext(tenantId, actorUserId, actorTenantId, impersonation));
        MDC.put(TENANT_ID_KEY, String.valueOf(tenantId));
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
            MDC.remove(ACTOR_USER_ID_KEY);
            MDC.remove(ACTOR_TENANT_ID_KEY);
            MDC.remove(IMPERSONATION_KEY);
        }
    }

    private Long parseLongHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("无效的Header数值: {}", value);
            return null;
        }
    }
}
