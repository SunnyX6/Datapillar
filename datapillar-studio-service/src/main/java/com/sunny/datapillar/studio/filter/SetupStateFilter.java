package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.enums.SetupBootstrapStatus;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Initialize status filter Responsible for initializing status request filtering and context
 * control
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@Slf4j
public class SetupStateFilter extends OncePerRequestFilter {

  private static final int SYSTEM_BOOTSTRAP_ID = 1;
  private static final long CACHE_TTL_MILLIS = 3000L;

  private static final Set<String> WHITELIST_PREFIX =
      Set.of("/actuator/health", "/actuator/info", "/v3/api-docs", "/setup");

  private final SystemBootstrapMapper systemBootstrapMapper;
  private final SecurityExceptionHandler securityExceptionHandler;

  private volatile CachedState cachedState = new CachedState(false, false, 0L);

  public SetupStateFilter(
      SystemBootstrapMapper systemBootstrapMapper,
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
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      CachedState state = currentState();
      if (!state.schemaReady()) {
        securityExceptionHandler.writeError(
            response,
            new com.sunny.datapillar.common.exception.RequiredException(
                ErrorType.REQUIRED,
                Map.of("reason", "SETUP_SCHEMA_NOT_READY"),
                "System initialization data is not ready"));
        return;
      }
      if (!state.setupCompleted()) {
        securityExceptionHandler.writeError(
            response,
            new com.sunny.datapillar.common.exception.RequiredException(
                ErrorType.REQUIRED,
                Map.of("reason", "SETUP_REQUIRED"),
                "The system has not yet completed initialization"));
        return;
      }
      chain.doFilter(request, response);
    } catch (Throwable ex) {
      log.error("Initialization status verification failed", ex);
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.ServiceUnavailableException(
              ex, "Service unavailable"));
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
        log.error("read system_bootstrap failed", ex);
        throw ex;
      }

      boolean schemaReady = bootstrap != null;
      boolean completed =
          schemaReady && SetupBootstrapStatus.COMPLETED.matches(bootstrap.getStatus());
      CachedState refreshed = new CachedState(schemaReady, completed, now + CACHE_TTL_MILLIS);
      cachedState = refreshed;
      return refreshed;
    }
  }

  private record CachedState(boolean schemaReady, boolean setupCompleted, long expiresAtMillis) {}
}
