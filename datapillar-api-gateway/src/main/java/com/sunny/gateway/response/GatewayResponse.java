package com.sunny.gateway.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网关统一响应格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse<T> {

    private String code;
    private String message;
    private T data;

    public static <T> GatewayResponse<T> ok(T data) {
        return new GatewayResponse<>(GatewayErrorCode.SUCCESS.getCode(), GatewayErrorCode.SUCCESS.getMessageTemplate(), data);
    }

    public static <T> GatewayResponse<T> ok() {
        return new GatewayResponse<>(GatewayErrorCode.SUCCESS.getCode(), GatewayErrorCode.SUCCESS.getMessageTemplate(), null);
    }

    public static <T> GatewayResponse<T> error(String message) {
        return new GatewayResponse<>(GatewayErrorCode.ERROR.getCode(), message, null);
    }

    public static <T> GatewayResponse<T> error(GatewayErrorCode errorCode, Object... args) {
        return new GatewayResponse<>(errorCode.getCode(), errorCode.formatMessage(args), null);
    }

    public static <T> GatewayResponse<T> error(String code, String message) {
        return new GatewayResponse<>(code, message, null);
    }
}
