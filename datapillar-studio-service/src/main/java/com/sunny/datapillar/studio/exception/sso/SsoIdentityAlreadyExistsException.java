package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * SSO 身份已存在异常
 * 描述用户身份绑定唯一约束冲突
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoIdentityAlreadyExistsException extends AlreadyExistsException {

    public SsoIdentityAlreadyExistsException() {
        super(ErrorType.SSO_IDENTITY_ALREADY_EXISTS, Map.of(), "SSO身份已存在");
    }

    public SsoIdentityAlreadyExistsException(Throwable cause) {
        super(cause, ErrorType.SSO_IDENTITY_ALREADY_EXISTS, Map.of(), "SSO身份已存在");
    }
}
