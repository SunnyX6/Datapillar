package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * Invitation has expired exception Describe the invalid semantics of the invitation code status
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationInactiveException extends ConflictException {

  public InvitationInactiveException() {
    super(ErrorType.INVITATION_INACTIVE, Map.of(), "The invitation code has expired");
  }

  public InvitationInactiveException(Throwable cause) {
    super(cause, ErrorType.INVITATION_INACTIVE, Map.of(), "The invitation code has expired");
  }
}
