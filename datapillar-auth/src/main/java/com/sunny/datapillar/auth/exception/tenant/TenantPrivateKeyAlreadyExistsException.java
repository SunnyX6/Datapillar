package com.sunny.datapillar.auth.exception.tenant;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * 租户私钥已存在异常
 * 描述租户私钥存储冲突语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class TenantPrivateKeyAlreadyExistsException extends AlreadyExistsException {

    public TenantPrivateKeyAlreadyExistsException() {
        super(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, Map.of(), "私钥文件已存在");
    }

    public TenantPrivateKeyAlreadyExistsException(Throwable cause) {
        super(cause, ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, Map.of(), "私钥文件已存在");
    }
}
