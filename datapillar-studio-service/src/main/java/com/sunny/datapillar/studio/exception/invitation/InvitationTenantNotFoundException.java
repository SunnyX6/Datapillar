package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * There is no exception when inviting tenants There is no semantics for describing tenant resources
 * in the invitation process.
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationTenantNotFoundException extends NotFoundException {

  public InvitationTenantNotFoundException(String message, Object... args) {
    super(ErrorType.INVITATION_TENANT_NOT_FOUND, Map.of(), message, args);
  }

  public InvitationTenantNotFoundException(Throwable cause, String message, Object... args) {
    super(cause, ErrorType.INVITATION_TENANT_NOT_FOUND, Map.of(), message, args);
  }
}
