package com.sunny.datapillar.studio.exception.tenant;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * There is an exception in the tenant encoding Describe tenant unique encoding constraint violation
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class TenantCodeAlreadyExistsException extends AlreadyExistsException {

  public TenantCodeAlreadyExistsException() {
    super(ErrorType.TENANT_CODE_ALREADY_EXISTS, Map.of(), "Tenant code already exists");
  }

  public TenantCodeAlreadyExistsException(Throwable cause) {
    super(cause, ErrorType.TENANT_CODE_ALREADY_EXISTS, Map.of(), "Tenant code already exists");
  }
}
