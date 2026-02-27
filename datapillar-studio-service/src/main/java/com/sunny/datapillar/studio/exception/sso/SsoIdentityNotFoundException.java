package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * SSO 身份不存在异常
 * 描述租户下身份绑定记录不存在语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoIdentityNotFoundException extends NotFoundException {

    public SsoIdentityNotFoundException() {
        super(ErrorType.SSO_IDENTITY_NOT_FOUND, Map.of(), "SSO身份不存在");
    }

    public SsoIdentityNotFoundException(Throwable cause) {
        super(cause, ErrorType.SSO_IDENTITY_NOT_FOUND, Map.of(), "SSO身份不存在");
    }
}
