package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * SSO 配置请求参数无效异常
 * 描述 SSO 配置请求参数不合法语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvalidSsoConfigRequestException extends BadRequestException {

    public InvalidSsoConfigRequestException() {
        super(ErrorType.SSO_CONFIG_REQUEST_INVALID, Map.of(), "SSO配置参数错误");
    }

    public InvalidSsoConfigRequestException(Throwable cause) {
        super(cause, ErrorType.SSO_CONFIG_REQUEST_INVALID, Map.of(), "SSO配置参数错误");
    }
}
