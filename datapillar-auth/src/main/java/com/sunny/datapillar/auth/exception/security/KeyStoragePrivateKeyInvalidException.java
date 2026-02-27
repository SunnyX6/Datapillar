package com.sunny.datapillar.auth.exception.security;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * 密钥存储私钥内容非法异常
 * 描述私钥内容为空或格式错误语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class KeyStoragePrivateKeyInvalidException extends BadRequestException {

    public KeyStoragePrivateKeyInvalidException(String message, Object... args) {
        super(ErrorType.BAD_REQUEST, Map.of(), message, args);
    }
}
