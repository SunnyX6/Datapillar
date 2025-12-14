package com.sunny.kg.tracing;

/**
 * 链路追踪接口
 * <p>
 * SDK 定义抽象接口，使用方可以接入 OpenTelemetry、Zipkin 等
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface Tracer {

    /**
     * 创建 Span
     *
     * @param operationName 操作名称
     * @return Span
     */
    Span startSpan(String operationName);

    /**
     * 获取当前 Span
     */
    Span currentSpan();

}
