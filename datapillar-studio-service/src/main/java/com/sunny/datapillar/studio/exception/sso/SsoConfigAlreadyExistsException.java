package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * SSO 配置已存在异常
 * 描述租户 SSO 配置唯一约束冲突
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigAlreadyExistsException extends AlreadyExistsException {

    public SsoConfigAlreadyExistsException() {
        super(ErrorType.SSO_CONFIG_ALREADY_EXISTS, Map.of(), "SSO配置已存在");
    }

    public SsoConfigAlreadyExistsException(Throwable cause) {
        super(cause, ErrorType.SSO_CONFIG_ALREADY_EXISTS, Map.of(), "SSO配置已存在");
    }
}
