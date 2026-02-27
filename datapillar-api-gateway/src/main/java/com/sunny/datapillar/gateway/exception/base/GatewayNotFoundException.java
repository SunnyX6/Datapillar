package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * GatewayNotFoundException语义异常
 * 抽象语义异常的默认具体实现
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayNotFoundException extends NotFoundException {

    public GatewayNotFoundException(String message, Object... args) {
        super(message, args);
    }

    public GatewayNotFoundException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }

    public GatewayNotFoundException(String type, Map<String, String> context, String message, Object... args) {
        super(type, context, message, args);
    }

    public GatewayNotFoundException(Throwable cause,
                  String type,
                  Map<String, String> context,
                  String message,
                  Object... args) {
        super(cause, type, context, message, args);
    }
}
