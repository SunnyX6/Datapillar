package com.sunny.datapillar.openlineage.security;

import com.sunny.datapillar.openlineage.exception.OpenLineageTenantMismatchException;
import com.sunny.datapillar.openlineage.exception.OpenLineageValidationException;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.TenantSourceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Tenant ownership resolver. */
@Component
public class TenantResolver {

  public TenantContext resolve(OpenLineageEventEnvelope envelope, HttpServletRequest request) {
    TrustedIdentityContext auth = TrustedIdentityContext.current(request);
    Long authTenantId = auth == null ? null : auth.tenantId();
    String authTenantCode = auth == null ? null : trimToNull(auth.tenantCode());
    Long resolvedTenantId = requirePositiveTenantId(authTenantId);
    String resolvedTenantCode = requireTenantCode(authTenantCode);
    String resolvedTenantName = resolvedTenantCode;

    Long facetTenantId = envelope.facetTenantId().orElse(null);
    String facetTenantCode = envelope.facetTenantCode().orElse(null);
    verifyFacetTenantConsistency(
        resolvedTenantId, resolvedTenantCode, facetTenantId, facetTenantCode);

    TenantSourceType sourceType =
        envelope.looksLikeGravitinoSource()
            ? TenantSourceType.GRAVITINO
            : TenantSourceType.COMPUTE_ENGINE;
    return new TenantContext(resolvedTenantId, resolvedTenantCode, resolvedTenantName, sourceType);
  }

  private Long requirePositiveTenantId(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new OpenLineageValidationException("authentication tenant_id missing");
    }
    return tenantId;
  }

  private String requireTenantCode(String tenantCode) {
    String normalized = trimToNull(tenantCode);
    if (normalized == null) {
      throw new OpenLineageValidationException("authentication tenant_code missing");
    }
    return normalized;
  }

  private void verifyFacetTenantConsistency(
      Long authTenantId, String authTenantCode, Long facetTenantId, String facetTenantCode) {
    if (facetTenantId != null && !authTenantId.equals(facetTenantId)) {
      throw new OpenLineageTenantMismatchException(
          "Tenants are inconsistent:authTenantId=%s facetTenantId=%s", authTenantId, facetTenantId);
    }
    String normalizedFacetTenantCode = trimToNull(facetTenantCode);
    if (normalizedFacetTenantCode != null && !authTenantCode.equals(normalizedFacetTenantCode)) {
      throw new OpenLineageTenantMismatchException(
          "Tenants are inconsistent:authTenantCode=%s facetTenantCode=%s",
          authTenantCode, normalizedFacetTenantCode);
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
