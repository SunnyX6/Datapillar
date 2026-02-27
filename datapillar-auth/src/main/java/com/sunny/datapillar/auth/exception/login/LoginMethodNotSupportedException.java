package com.sunny.datapillar.auth.exception.login;

import com.sunny.datapillar.common.exception.BadRequestException;

/**
 * 登录MethodNotSupported异常
 * 描述登录MethodNotSupported异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LoginMethodNotSupportedException extends BadRequestException {

    public LoginMethodNotSupportedException(String requestedMethod) {
        super("不支持的登录方法: %s", requestedMethod == null ? "null" : requestedMethod);
    }
}
