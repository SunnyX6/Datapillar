package com.sunny.datapillar.auth.exception.tenant;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * Exception for tenant private-key storage conflicts.
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class TenantPrivateKeyAlreadyExistsException extends AlreadyExistsException {

  public TenantPrivateKeyAlreadyExistsException() {
    super(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, Map.of(), "Private key file already exists");
  }

  public TenantPrivateKeyAlreadyExistsException(Throwable cause) {
    super(
        cause,
        ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
        Map.of(),
        "Private key file already exists");
  }
}
