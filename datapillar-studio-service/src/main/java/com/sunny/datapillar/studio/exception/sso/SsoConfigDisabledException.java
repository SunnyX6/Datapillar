package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ForbiddenException;
import java.util.Map;

/**
 * SSO 配置已禁用异常
 * 描述租户 SSO 配置不可用语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigDisabledException extends ForbiddenException {

    public SsoConfigDisabledException() {
        super(ErrorType.SSO_CONFIG_DISABLED, Map.of(), "SSO配置已禁用");
    }

    public SsoConfigDisabledException(Throwable cause) {
        super(cause, ErrorType.SSO_CONFIG_DISABLED, Map.of(), "SSO配置已禁用");
    }
}
