package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.RequiredException;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 初始化状态过滤器
 * 负责初始化状态请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@Slf4j
public class SetupStateFilter extends OncePerRequestFilter {

    private static final int SYSTEM_BOOTSTRAP_ID = 1;
    private static final int SETUP_COMPLETED = 1;
    private static final long CACHE_TTL_MILLIS = 3000L;

    private static final Set<String> WHITELIST_PREFIX = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/setup"
    );

    private final SystemBootstrapMapper systemBootstrapMapper;
    private final SecurityExceptionHandler securityExceptionHandler;

    private volatile CachedState cachedState = new CachedState(false, false, 0L);

    public SetupStateFilter(SystemBootstrapMapper systemBootstrapMapper,
                            SecurityExceptionHandler securityExceptionHandler) {
        this.systemBootstrapMapper = systemBootstrapMapper;
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
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
        try {
            CachedState state = currentState();
            if (!state.schemaReady()) {
                securityExceptionHandler.writeError(
                        response,
                        new com.sunny.datapillar.common.exception.RequiredException(
                                ErrorType.REQUIRED,
                                Map.of("reason", "SETUP_SCHEMA_NOT_READY"),
                                "系统初始化数据未就绪"));
                return;
            }
            if (!state.setupCompleted()) {
                securityExceptionHandler.writeError(
                        response,
                        new com.sunny.datapillar.common.exception.RequiredException(
                                ErrorType.REQUIRED,
                                Map.of("reason", "SETUP_REQUIRED"),
                                "系统尚未完成初始化"));
                return;
            }
            chain.doFilter(request, response);
        } catch (Exception ex) {
            log.error("初始化状态校验失败", ex);
            securityExceptionHandler.writeError(response, new com.sunny.datapillar.common.exception.ServiceUnavailableException(ex, "服务不可用"));
        }
    }

    private CachedState currentState() {
        long now = System.currentTimeMillis();
        CachedState state = cachedState;
        if (now < state.expiresAtMillis()) {
            return state;
        }

        synchronized (this) {
            CachedState latest = cachedState;
            if (now < latest.expiresAtMillis()) {
                return latest;
            }

            SystemBootstrap bootstrap;
            try {
                bootstrap = systemBootstrapMapper.selectById(SYSTEM_BOOTSTRAP_ID);
            } catch (RuntimeException ex) {
                log.error("读取 system_bootstrap 失败", ex);
                throw ex;
            }

            boolean schemaReady = bootstrap != null;
            boolean completed = schemaReady && bootstrap.getSetupCompleted() != null
                    && bootstrap.getSetupCompleted() == SETUP_COMPLETED;
            CachedState refreshed = new CachedState(schemaReady, completed, now + CACHE_TTL_MILLIS);
            cachedState = refreshed;
            return refreshed;
        }
    }

    private record CachedState(boolean schemaReady, boolean setupCompleted, long expiresAtMillis) {
    }
}
