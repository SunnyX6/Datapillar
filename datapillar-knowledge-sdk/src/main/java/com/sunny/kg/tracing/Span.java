package com.sunny.kg.tracing;

import java.util.Map;

/**
 * Span 接口
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface Span extends AutoCloseable {

    /**
     * 设置标签
     */
    Span setTag(String key, String value);

    /**
     * 设置多个标签
     */
    Span setTags(Map<String, String> tags);

    /**
     * 记录事件
     */
    Span log(String event);

    /**
     * 记录异常
     */
    Span logError(Throwable throwable);

    /**
     * 设置状态为成功
     */
    Span setSuccess();

    /**
     * 设置状态为失败
     */
    Span setError(String message);

    /**
     * 获取 Trace ID
     */
    String getTraceId();

    /**
     * 获取 Span ID
     */
    String getSpanId();

    /**
     * 结束 Span
     */
    @Override
    void close();

}
