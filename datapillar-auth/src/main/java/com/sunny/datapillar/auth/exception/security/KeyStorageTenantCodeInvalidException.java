package com.sunny.datapillar.auth.exception.security;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * 密钥存储租户编码非法异常
 * 描述 tenantCode 非法输入语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class KeyStorageTenantCodeInvalidException extends BadRequestException {

    public KeyStorageTenantCodeInvalidException(String message, Object... args) {
        super(ErrorType.BAD_REQUEST, Map.of(), message, args);
    }
}
