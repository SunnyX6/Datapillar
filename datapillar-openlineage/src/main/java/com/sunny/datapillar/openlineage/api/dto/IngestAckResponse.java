package com.sunny.datapillar.openlineage.api.dto;

import lombok.Builder;

/**
 * ingest 应答。
 */
@Builder
public record IngestAckResponse(
        String status,
        String eventType,
        String runId,
        Long tenantId
) {
}
