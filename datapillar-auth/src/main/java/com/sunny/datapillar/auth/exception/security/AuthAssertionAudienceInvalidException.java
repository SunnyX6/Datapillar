package com.sunny.datapillar.auth.exception.security;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * 认证断言 audience 非法异常
 * 描述认证断言签名参数缺失语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class AuthAssertionAudienceInvalidException extends BadRequestException {

    public AuthAssertionAudienceInvalidException(String message, Object... args) {
        super(ErrorType.BAD_REQUEST, Map.of(), message, args);
    }
}
