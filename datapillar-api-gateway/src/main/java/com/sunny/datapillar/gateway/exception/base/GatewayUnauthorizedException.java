package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * GatewayUnauthorizedException语义异常
 * 抽象语义异常的默认具体实现
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayUnauthorizedException extends UnauthorizedException {

    public GatewayUnauthorizedException(String message, Object... args) {
        super(message, args);
    }

    public GatewayUnauthorizedException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }

    public GatewayUnauthorizedException(String type, Map<String, String> context, String message, Object... args) {
        super(type, context, message, args);
    }

    public GatewayUnauthorizedException(Throwable cause,
                  String type,
                  Map<String, String> context,
                  String message,
                  Object... args) {
        super(cause, type, context, message, args);
    }
}
