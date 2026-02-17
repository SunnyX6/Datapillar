package com.sunny.datapillar.gateway.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 网关响应模型
 * 定义网关响应数据结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse<T> {

    private String code;
    private String message;
    private String type;
    private List<String> stack;
    private T data;

    public GatewayResponse(String code, String message, T data) {
        this(code, message, null, null, data);
    }

    public static <T> GatewayResponse<T> ok(T data) {
        return new GatewayResponse<>(GatewayErrorCode.SUCCESS.getCode(), GatewayErrorCode.SUCCESS.getMessageTemplate(), data);
    }

    public static <T> GatewayResponse<T> ok() {
        return new GatewayResponse<>(GatewayErrorCode.SUCCESS.getCode(), GatewayErrorCode.SUCCESS.getMessageTemplate(), null);
    }

    public static <T> GatewayResponse<T> error(String message) {
        return new GatewayResponse<>(GatewayErrorCode.ERROR.getCode(), message, null, null, null);
    }

    public static <T> GatewayResponse<T> error(GatewayErrorCode errorCode, Object... args) {
        return new GatewayResponse<>(errorCode.getCode(), errorCode.formatMessage(args), null, null, null);
    }

    public static <T> GatewayResponse<T> error(String code, String message) {
        return new GatewayResponse<>(code, message, null, null, null);
    }

    public static <T> GatewayResponse<T> error(String code, String type, String message, List<String> stack) {
        return new GatewayResponse<>(code, message, type, stack, null);
    }
}
