package com.sunny.kg.spi;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 拦截器上下文
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class InterceptorContext {

    private final String operationType;
    private final Object payload;
    private final Instant timestamp;
    private final Map<String, Object> attributes;

    private long durationMs;
    private boolean success;

    public InterceptorContext(String operationType, Object payload) {
        this.operationType = operationType;
        this.payload = payload;
        this.timestamp = Instant.now();
        this.attributes = new HashMap<>();
    }

    public String getOperationType() {
        return operationType;
    }

    public Object getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

}
