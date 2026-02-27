package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * SSO 配置不存在异常
 * 描述租户指定供应商配置不存在语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigNotFoundException extends NotFoundException {

    public SsoConfigNotFoundException() {
        super(ErrorType.SSO_CONFIG_NOT_FOUND, Map.of(), "SSO配置不存在");
    }

    public SsoConfigNotFoundException(Throwable cause) {
        super(cause, ErrorType.SSO_CONFIG_NOT_FOUND, Map.of(), "SSO配置不存在");
    }
}
