package com.sunny.kg.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * 基于日志的 Tracer 实现
 * <p>
 * 将 traceId/spanId 写入 MDC，配合日志框架输出
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class LoggingTracer implements Tracer {

    private static final Logger log = LoggerFactory.getLogger(LoggingTracer.class);
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private final ThreadLocal<LoggingSpan> currentSpan = new ThreadLocal<>();

    @Override
    public Span startSpan(String operationName) {
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId == null) {
            traceId = generateId(16);
        }
        String spanId = generateId(8);

        LoggingSpan span = new LoggingSpan(operationName, traceId, spanId);
        currentSpan.set(span);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);

        log.debug("[SPAN_START] operation={}", operationName);
        return span;
    }

    @Override
    public Span currentSpan() {
        LoggingSpan span = currentSpan.get();
        return span != null ? span : NoopTracer.INSTANCE.currentSpan();
    }

    private String generateId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

    private class LoggingSpan implements Span {
        private final String operationName;
        private final String traceId;
        private final String spanId;
        private final long startTime;
        private boolean success = true;
        private String errorMessage;

        LoggingSpan(String operationName, String traceId, String spanId) {
            this.operationName = operationName;
            this.traceId = traceId;
            this.spanId = spanId;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public Span setTag(String key, String value) {
            MDC.put(key, value);
            return this;
        }

        @Override
        public Span setTags(Map<String, String> tags) {
            tags.forEach(MDC::put);
            return this;
        }

        @Override
        public Span log(String event) {
            log.debug("[SPAN_EVENT] {}", event);
            return this;
        }

        @Override
        public Span logError(Throwable throwable) {
            log.error("[SPAN_ERROR] {}", throwable.getMessage(), throwable);
            this.success = false;
            this.errorMessage = throwable.getMessage();
            return this;
        }

        @Override
        public Span setSuccess() {
            this.success = true;
            return this;
        }

        @Override
        public Span setError(String message) {
            this.success = false;
            this.errorMessage = message;
            return this;
        }

        @Override
        public String getTraceId() { return traceId; }

        @Override
        public String getSpanId() { return spanId; }

        @Override
        public void close() {
            long duration = System.currentTimeMillis() - startTime;
            if (success) {
                log.debug("[SPAN_END] operation={}, duration={}ms, status=OK", operationName, duration);
            } else {
                log.warn("[SPAN_END] operation={}, duration={}ms, status=ERROR, error={}",
                        operationName, duration, errorMessage);
            }
            currentSpan.remove();
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        }
    }

}
