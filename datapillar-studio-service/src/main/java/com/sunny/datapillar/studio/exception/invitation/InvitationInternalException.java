package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;

/**
 * Invite internal error exception Describe invitation process unrecoverable system error semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationInternalException extends InternalException {

  public InvitationInternalException() {
    super(ErrorType.INVITATION_INTERNAL_ERROR, Map.of(), "Invitation processing failed");
  }

  public InvitationInternalException(Throwable cause) {
    super(cause, ErrorType.INVITATION_INTERNAL_ERROR, Map.of(), "Invitation processing failed");
  }
}
