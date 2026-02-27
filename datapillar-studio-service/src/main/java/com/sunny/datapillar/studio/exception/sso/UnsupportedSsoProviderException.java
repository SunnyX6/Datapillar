package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * 不支持的 SSO 供应商异常
 * 描述当前租户请求了未支持的 SSO 供应商语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class UnsupportedSsoProviderException extends BadRequestException {

    public UnsupportedSsoProviderException() {
        super(ErrorType.SSO_PROVIDER_UNSUPPORTED, Map.of(), "不支持的SSO供应商");
    }

    public UnsupportedSsoProviderException(Throwable cause) {
        super(cause, ErrorType.SSO_PROVIDER_UNSUPPORTED, Map.of(), "不支持的SSO供应商");
    }
}
