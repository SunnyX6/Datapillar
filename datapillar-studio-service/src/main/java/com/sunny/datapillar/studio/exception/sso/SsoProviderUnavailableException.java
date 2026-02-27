package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import java.util.Map;

/**
 * SSO 供应商不可用异常
 * 描述外部 SSO 供应商暂时不可用语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoProviderUnavailableException extends ServiceUnavailableException {

    public SsoProviderUnavailableException() {
        super(ErrorType.SSO_PROVIDER_UNAVAILABLE, Map.of(), "SSO服务暂不可用");
    }

    public SsoProviderUnavailableException(Throwable cause) {
        super(cause, ErrorType.SSO_PROVIDER_UNAVAILABLE, Map.of(), "SSO服务暂不可用");
    }
}
