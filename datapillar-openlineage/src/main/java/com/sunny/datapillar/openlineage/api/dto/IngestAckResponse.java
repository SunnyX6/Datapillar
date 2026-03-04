package com.sunny.datapillar.openlineage.api.dto;

import lombok.Builder;

/** ingest reply. */
@Builder
public record IngestAckResponse(String status, String eventType, String runId, Long tenantId) {}
