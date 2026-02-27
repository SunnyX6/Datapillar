package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;

/**
 * SSO 配置无效异常
 * 描述服务端持久化配置内容不合法语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigInvalidException extends InternalException {

    public SsoConfigInvalidException() {
        super(ErrorType.SSO_CONFIG_INVALID, Map.of(), "SSO配置无效");
    }

    public SsoConfigInvalidException(Throwable cause) {
        super(cause, ErrorType.SSO_CONFIG_INVALID, Map.of(), "SSO配置无效");
    }
}
