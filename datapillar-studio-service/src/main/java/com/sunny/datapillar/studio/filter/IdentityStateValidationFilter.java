package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates local user, tenant, and membership state after tenant context initialization. */
@Slf4j
@Component
public class IdentityStateValidationFilter extends OncePerRequestFilter {

  private final TrustedIdentityProperties properties;
  private final UserMapper userMapper;
  private final TenantUserMapper tenantUserMapper;
  private final TenantMapper tenantMapper;
  private final SecurityExceptionHandler securityExceptionHandler;

  public IdentityStateValidationFilter(
      TrustedIdentityProperties properties,
      UserMapper userMapper,
      TenantUserMapper tenantUserMapper,
      TenantMapper tenantMapper,
      SecurityExceptionHandler securityExceptionHandler) {
    this.properties = properties;
    this.userMapper = userMapper;
    this.tenantUserMapper = tenantUserMapper;
    this.tenantMapper = tenantMapper;
    this.securityExceptionHandler = securityExceptionHandler;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return TrustedIdentityRequestSupport.shouldSkip(request, properties.isEnabled());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    TrustedIdentityContext context = TrustedIdentityContext.current(request);
    if (context == null) {
      reject(request, response, new UnauthorizedException("trusted_identity_context_missing"));
      return;
    }

    try {
      validateLocalIdentityState(context);
    } catch (DatapillarRuntimeException exception) {
      reject(request, response, exception);
      return;
    } catch (RuntimeException exception) {
      log.error(
          "security_event event=identity_state_validation_error path={} method={} reason={}",
          TrustedIdentityRequestSupport.normalizedPath(request),
          request.getMethod(),
          exception.getMessage(),
          exception);
      securityExceptionHandler.writeError(
          response, new InternalException(exception, "Server internal error"));
      return;
    }

    chain.doFilter(request, response);
  }

  private void validateLocalIdentityState(TrustedIdentityContext context) {
    PrincipalType principalType = context.principalType();
    Long userId = context.userId();
    Long tenantId = context.tenantId();
    String tenantCode = context.tenantCode();

    if (principalType == null || tenantId == null) {
      throw new UnauthorizedException("trusted_user_context_missing");
    }
    if (!StringUtils.hasText(tenantCode)) {
      throw new UnauthorizedException("trusted_identity_tenant_code_missing");
    }

    Tenant tenant = tenantMapper.selectByCode(tenantCode);
    if (tenant == null || tenant.getId() == null) {
      throw new UnauthorizedException("tenant_not_found");
    }
    if (!tenantId.equals(tenant.getId())) {
      throw new UnauthorizedException("trusted_tenant_context_mismatch");
    }
    if (tenant.getStatus() == null || tenant.getStatus() != 1) {
      throw new ForbiddenException("tenant_disabled");
    }

    if (principalType == PrincipalType.API_KEY) {
      return;
    }

    if (userId == null) {
      throw new UnauthorizedException("trusted_user_context_missing");
    }

    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new UnauthorizedException("trusted_user_not_found");
    }
    if (user.getStatus() == null || user.getStatus() != 1) {
      throw new ForbiddenException("user_disabled");
    }

    if (context.impersonation()) {
      return;
    }

    TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
    if (tenantUser == null) {
      throw new ForbiddenException("tenant_membership_missing");
    }
    if (tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
      throw new ForbiddenException("tenant_membership_disabled");
    }
  }

  private void reject(
      HttpServletRequest request,
      HttpServletResponse response,
      DatapillarRuntimeException exception)
      throws IOException {
    log.warn(
        "security_event event=identity_state_validation_failed path={} method={} reason={}",
        TrustedIdentityRequestSupport.normalizedPath(request),
        request.getMethod(),
        exception.getMessage());
    securityExceptionHandler.writeError(response, exception);
  }
}
