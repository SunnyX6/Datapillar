package com.sunny.datapillar.studio.exception.tenant;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * 租户编码已存在异常
 * 描述租户唯一编码约束冲突
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class TenantCodeAlreadyExistsException extends AlreadyExistsException {

    public TenantCodeAlreadyExistsException() {
        super(ErrorType.TENANT_CODE_ALREADY_EXISTS, Map.of(), "租户编码已存在");
    }

    public TenantCodeAlreadyExistsException(Throwable cause) {
        super(cause, ErrorType.TENANT_CODE_ALREADY_EXISTS, Map.of(), "租户编码已存在");
    }
}
