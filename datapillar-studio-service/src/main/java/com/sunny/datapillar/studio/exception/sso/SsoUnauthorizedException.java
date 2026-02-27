package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * SSO 未授权异常
 * 描述租户上下文缺失导致的未授权语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoUnauthorizedException extends UnauthorizedException {

    public SsoUnauthorizedException() {
        super(ErrorType.SSO_UNAUTHORIZED, Map.of(), "未授权访问");
    }

    public SsoUnauthorizedException(Throwable cause) {
        super(cause, ErrorType.SSO_UNAUTHORIZED, Map.of(), "未授权访问");
    }
}
