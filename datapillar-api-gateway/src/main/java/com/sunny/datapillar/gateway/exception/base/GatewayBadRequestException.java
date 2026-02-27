package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * GatewayBadRequestException语义异常
 * 抽象语义异常的默认具体实现
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayBadRequestException extends BadRequestException {

    public GatewayBadRequestException(String message, Object... args) {
        super(message, args);
    }

    public GatewayBadRequestException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }

    public GatewayBadRequestException(String type, Map<String, String> context, String message, Object... args) {
        super(type, context, message, args);
    }

    public GatewayBadRequestException(Throwable cause,
                  String type,
                  Map<String, String> context,
                  String message,
                  Object... args) {
        super(cause, type, context, message, args);
    }
}
