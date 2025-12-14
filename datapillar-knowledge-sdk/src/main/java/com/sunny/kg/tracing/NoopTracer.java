package com.sunny.kg.tracing;

import java.util.Map;
import java.util.UUID;

/**
 * 空实现 Tracer（默认不追踪）
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class NoopTracer implements Tracer {

    public static final NoopTracer INSTANCE = new NoopTracer();

    @Override
    public Span startSpan(String operationName) {
        return NoopSpan.INSTANCE;
    }

    @Override
    public Span currentSpan() {
        return NoopSpan.INSTANCE;
    }

    private static class NoopSpan implements Span {
        static final NoopSpan INSTANCE = new NoopSpan();
        private final String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        private final String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        @Override
        public Span setTag(String key, String value) { return this; }

        @Override
        public Span setTags(Map<String, String> tags) { return this; }

        @Override
        public Span log(String event) { return this; }

        @Override
        public Span logError(Throwable throwable) { return this; }

        @Override
        public Span setSuccess() { return this; }

        @Override
        public Span setError(String message) { return this; }

        @Override
        public String getTraceId() { return traceId; }

        @Override
        public String getSpanId() { return spanId; }

        @Override
        public void close() {}
    }

}
