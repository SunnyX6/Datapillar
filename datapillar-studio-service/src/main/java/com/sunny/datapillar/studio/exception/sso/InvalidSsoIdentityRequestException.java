package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * SSO 身份请求参数无效异常
 * 描述 SSO 身份绑定请求参数不合法语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvalidSsoIdentityRequestException extends BadRequestException {

    public InvalidSsoIdentityRequestException() {
        super(ErrorType.SSO_IDENTITY_REQUEST_INVALID, Map.of(), "SSO身份参数错误");
    }

    public InvalidSsoIdentityRequestException(Throwable cause) {
        super(cause, ErrorType.SSO_IDENTITY_REQUEST_INVALID, Map.of(), "SSO身份参数错误");
    }
}
