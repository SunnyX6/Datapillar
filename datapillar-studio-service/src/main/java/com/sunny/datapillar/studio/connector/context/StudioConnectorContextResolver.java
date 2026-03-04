package com.sunny.datapillar.studio.connector.context;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.connector.runtime.context.ConnectorContextResolver;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Resolve connector context from trusted tenant and security context. */
@Component
public class StudioConnectorContextResolver implements ConnectorContextResolver {

  @Override
  public ConnectorContext resolve() {
    TenantContext tenantContext = TenantContextHolder.get();
    HttpServletRequest request = currentRequest();
    TrustedIdentityContext identityContext = TrustedIdentityContext.current(request);

    Long tenantId =
        tenantContext != null
            ? tenantContext.getTenantId()
            : identityContext == null ? null : identityContext.tenantId();
    String tenantCode =
        tenantContext != null
            ? tenantContext.getTenantCode()
            : identityContext == null ? null : identityContext.tenantCode();
    Long userId = identityContext == null ? null : identityContext.userId();
    String username = identityContext == null ? null : identityContext.username();

    Long actorUserId =
        tenantContext != null
            ? tenantContext.getActorUserId()
            : identityContext == null ? null : identityContext.actorUserId();
    Long actorTenantId =
        tenantContext != null
            ? tenantContext.getActorTenantId()
            : identityContext == null ? null : identityContext.actorTenantId();
    boolean impersonation =
        tenantContext != null
            ? tenantContext.isImpersonation()
            : identityContext != null && identityContext.impersonation();

    String principalSub = readHeader(request, HeaderConstants.HEADER_PRINCIPAL_SUB);
    String traceId = readHeader(request, HeaderConstants.HEADER_TRACE_ID);
    String requestId = readHeader(request, HeaderConstants.HEADER_REQUEST_ID);

    return new ConnectorContext(
        tenantId,
        tenantCode,
        userId,
        username,
        principalSub,
        actorUserId,
        actorTenantId,
        impersonation,
        traceId,
        requestId);
  }

  private HttpServletRequest currentRequest() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
      return servletRequestAttributes.getRequest();
    }
    return null;
  }

  private String readHeader(HttpServletRequest request, String key) {
    if (request == null || !StringUtils.hasText(key)) {
      return null;
    }
    String value = request.getHeader(key);
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
