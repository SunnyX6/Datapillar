package com.sunny.datapillar.openlineage.security;

import com.sunny.datapillar.openlineage.exception.OpenLineageTenantMismatchException;
import com.sunny.datapillar.openlineage.exception.OpenLineageValidationException;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.TenantSourceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 租户归属解析器。
 */
@Component
public class TenantResolver {

    public TenantContext resolve(OpenLineageEventEnvelope envelope, HttpServletRequest request) {
        GatewayAssertionContext auth = GatewayAssertionContext.current(request);

        Long authTenantId = auth == null ? null : auth.tenantId();
        String authTenantCode = auth == null ? null : trimToNull(auth.tenantCode());
        String authTenantName = auth == null ? null : trimToNull(auth.tenantName());

        Long facetTenantId = envelope.facetTenantId().orElse(null);
        String facetTenantCode = envelope.facetTenantCode().orElse(null);
        String facetTenantName = envelope.facetTenantName().orElse(null);

        if (envelope.looksLikeGravitinoSource()) {
            if (facetTenantId == null || facetTenantId <= 0) {
                throw new OpenLineageValidationException("gravitino 事件缺少 facet tenant_id");
            }
            if (authTenantId != null && !authTenantId.equals(facetTenantId)) {
                throw new OpenLineageTenantMismatchException(
                        "租户不一致: authTenantId=%s facetTenantId=%s", authTenantId, facetTenantId);
            }
            String resolvedTenantCode = firstNonBlank(facetTenantCode, authTenantCode);
            String resolvedTenantName = firstNonBlank(facetTenantName, authTenantName);
            ensureTenantDetail(resolvedTenantCode, "tenantCode");
            ensureTenantDetail(resolvedTenantName, "tenantName");
            return new TenantContext(
                    facetTenantId,
                    resolvedTenantCode,
                    resolvedTenantName,
                    TenantSourceType.GRAVITINO);
        }

        if (authTenantId == null || authTenantId <= 0) {
            throw new OpenLineageValidationException("compute 事件缺少鉴权租户");
        }
        if (facetTenantId != null && !authTenantId.equals(facetTenantId)) {
            throw new OpenLineageTenantMismatchException(
                    "租户不一致: authTenantId=%s facetTenantId=%s", authTenantId, facetTenantId);
        }

        String resolvedTenantCode = firstNonBlank(authTenantCode, facetTenantCode);
        String resolvedTenantName = firstNonBlank(authTenantName, facetTenantName);
        ensureTenantDetail(resolvedTenantCode, "tenantCode");
        ensureTenantDetail(resolvedTenantName, "tenantName");

        return new TenantContext(
                authTenantId,
                resolvedTenantCode,
                resolvedTenantName,
                TenantSourceType.COMPUTE_ENGINE);
    }

    private String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = trimToNull(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        return trimToNull(fallback);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void ensureTenantDetail(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new OpenLineageValidationException("缺少租户字段: %s", fieldName);
        }
    }
}
