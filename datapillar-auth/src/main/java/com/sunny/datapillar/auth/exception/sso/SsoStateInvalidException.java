package com.sunny.datapillar.auth.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * SSO 状态无效异常
 * 描述 SSO state 非法或过期语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoStateInvalidException extends UnauthorizedException {

    public SsoStateInvalidException() {
        super(ErrorType.SSO_STATE_INVALID, Map.of(), "SSO state 无效");
    }

    public SsoStateInvalidException(Throwable cause) {
        super(cause, ErrorType.SSO_STATE_INVALID, Map.of(), "SSO state 无效");
    }
}
