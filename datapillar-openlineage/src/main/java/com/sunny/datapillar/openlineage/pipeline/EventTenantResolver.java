package com.sunny.datapillar.openlineage.pipeline;

import com.sunny.datapillar.openlineage.exception.OpenLineageTenantMismatchException;
import com.sunny.datapillar.openlineage.exception.OpenLineageValidationException;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.source.OpenLineageSourceModels;
import com.sunny.datapillar.openlineage.source.event.OpenLineageEvent;
import org.springframework.stereotype.Component;

/** Resolves and validates tenant ownership for event consume flow. */
@Component
public class EventTenantResolver {

  public Tenant resolve(Long headerTenantId, String headerTenantCode, OpenLineageEvent event) {
    Long facetTenantId = event == null ? null : event.facetTenantId().orElse(null);
    String facetTenantCode = event == null ? null : event.facetTenantCode().orElse(null);
    String facetTenantName = event == null ? null : event.facetTenantName().orElse(null);
    return resolveTenant(
        headerTenantId, headerTenantCode, facetTenantId, facetTenantCode, facetTenantName);
  }

  public Tenant resolve(
      Long headerTenantId, String headerTenantCode, OpenLineageSourceModels models) {
    Long facetTenantId = models == null ? null : models.getFacetTenantId();
    String facetTenantCode = models == null ? null : models.getFacetTenantCode();
    String facetTenantName = models == null ? null : models.getFacetTenantName();
    return resolveTenant(
        headerTenantId, headerTenantCode, facetTenantId, facetTenantCode, facetTenantName);
  }

  private Tenant resolveTenant(
      Long headerTenantId,
      String headerTenantCode,
      Long facetTenantId,
      String facetTenantCode,
      String facetTenantName) {
    Long tenantId = requirePositiveTenantId(headerTenantId);
    String normalizedHeaderTenantCode = trimToNull(headerTenantCode);

    if (facetTenantId != null && !tenantId.equals(facetTenantId)) {
      throw new OpenLineageTenantMismatchException(
          "Tenants are inconsistent:headerTenantId=%s facetTenantId=%s", tenantId, facetTenantId);
    }

    String normalizedFacetTenantCode = trimToNull(facetTenantCode);
    if (normalizedHeaderTenantCode != null
        && normalizedFacetTenantCode != null
        && !normalizedHeaderTenantCode.equals(normalizedFacetTenantCode)) {
      throw new OpenLineageTenantMismatchException(
          "Tenants are inconsistent:headerTenantCode=%s facetTenantCode=%s",
          normalizedHeaderTenantCode, normalizedFacetTenantCode);
    }

    String resolvedTenantCode =
        firstNonBlank(normalizedHeaderTenantCode, normalizedFacetTenantCode);
    if (resolvedTenantCode == null) {
      throw new OpenLineageValidationException("event message tenantCode is invalid");
    }
    String resolvedTenantName = firstNonBlank(trimToNull(facetTenantName), resolvedTenantCode);

    Tenant tenant = new Tenant();
    tenant.setTenantId(tenantId);
    tenant.setTenantCode(resolvedTenantCode);
    tenant.setTenantName(resolvedTenantName);
    return tenant;
  }

  private Long requirePositiveTenantId(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new OpenLineageValidationException("event message tenantId is invalid");
    }
    return tenantId;
  }

  private String firstNonBlank(String first, String second) {
    if (trimToNull(first) != null) {
      return trimToNull(first);
    }
    return trimToNull(second);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
