package com.sunny.datapillar.auth.exception.security;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * 密钥存储配置非法异常
 * 描述密钥存储配置参数错误语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class KeyStorageConfigInvalidException extends BadRequestException {

    public KeyStorageConfigInvalidException(String message, Object... args) {
        super(ErrorType.BAD_REQUEST, Map.of(), message, args);
    }
}
