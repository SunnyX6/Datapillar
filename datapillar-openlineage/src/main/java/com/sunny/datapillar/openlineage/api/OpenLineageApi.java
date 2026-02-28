package com.sunny.datapillar.openlineage.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.api.dto.IngestAckResponse;
import com.sunny.datapillar.openlineage.exception.OpenLineageTenantMismatchException;
import com.sunny.datapillar.openlineage.exception.OpenLineageValidationException;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.security.TenantContext;
import com.sunny.datapillar.openlineage.security.TenantContextHolder;
import com.sunny.datapillar.openlineage.security.TenantResolver;
import com.sunny.datapillar.openlineage.service.OpenLineageService;
import io.openlineage.client.OpenLineage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenLineage ingest API。
 */
@RestController
public class OpenLineageApi {

    private final ObjectMapper openLineageObjectMapper;
    private final OpenLineageService openLineageService;
    private final TenantResolver tenantResolver;

    public OpenLineageApi(@Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper,
                          OpenLineageService openLineageService,
                          TenantResolver tenantResolver) {
        this.openLineageObjectMapper = openLineageObjectMapper;
        this.openLineageService = openLineageService;
        this.tenantResolver = tenantResolver;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<IngestAckResponse>> ingest(@RequestBody JsonNode payload,
                                                                        HttpServletRequest request) {
        OpenLineageEventEnvelope envelope = parseEnvelope(payload);
        TenantContext tenantContext = tenantResolver.resolve(envelope, request);

        TenantContextHolder.set(tenantContext);
        try {
            CompletableFuture<Void> createFuture = dispatchCreateAsync(envelope, tenantContext);
            return createFuture.handle((unused, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrap(throwable);
                    HttpStatus status = resolveStatus(cause);
                    return ResponseEntity.status(status).body(IngestAckResponse.builder()
                            .status(status.is4xxClientError() ? "bad_request" : "internal_error")
                            .eventType(envelope.getInternalEventType())
                            .runId(envelope.runId())
                            .tenantId(tenantContext.tenantId())
                            .build());
                }
                return ResponseEntity.status(HttpStatus.CREATED).body(IngestAckResponse.builder()
                        .status("accepted")
                        .eventType(envelope.getInternalEventType())
                        .runId(envelope.runId())
                        .tenantId(tenantContext.tenantId())
                        .build());
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CompletableFuture<Void> dispatchCreateAsync(OpenLineageEventEnvelope envelope,
                                                        TenantContext tenantContext) {
        OpenLineage.BaseEvent event = envelope.getEvent();
        if (event instanceof OpenLineage.RunEvent runEvent) {
            return openLineageService.createAsync(runEvent, envelope, tenantContext);
        }
        if (event instanceof OpenLineage.DatasetEvent datasetEvent) {
            return openLineageService.createAsync(datasetEvent, envelope, tenantContext);
        }
        if (event instanceof OpenLineage.JobEvent jobEvent) {
            return openLineageService.createAsync(jobEvent, envelope, tenantContext);
        }
        throw new OpenLineageValidationException("不支持的 OpenLineage 事件类型");
    }

    private OpenLineageEventEnvelope parseEnvelope(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new OpenLineageValidationException("请求体必须是合法 JSON 对象");
        }

        try {
            if (payload.path("run").isObject()) {
                OpenLineage.RunEvent event = openLineageObjectMapper.treeToValue(payload, OpenLineage.RunEvent.class);
                return OpenLineageEventEnvelope.fromRunEvent(event, payload);
            }
            if (payload.path("dataset").isObject()) {
                OpenLineage.DatasetEvent event =
                        openLineageObjectMapper.treeToValue(payload, OpenLineage.DatasetEvent.class);
                return OpenLineageEventEnvelope.fromDatasetEvent(event, payload);
            }
            if (payload.path("job").isObject()) {
                OpenLineage.JobEvent event = openLineageObjectMapper.treeToValue(payload, OpenLineage.JobEvent.class);
                return OpenLineageEventEnvelope.fromJobEvent(event, payload);
            }
        } catch (JsonProcessingException ex) {
            throw new OpenLineageValidationException(ex, "OpenLineage 事件反序列化失败: %s", ex.getOriginalMessage());
        }

        throw new OpenLineageValidationException("无法识别 OpenLineage 事件类型");
    }

    private HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof OpenLineageValidationException
                || throwable instanceof OpenLineageTenantMismatchException
                || throwable instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
