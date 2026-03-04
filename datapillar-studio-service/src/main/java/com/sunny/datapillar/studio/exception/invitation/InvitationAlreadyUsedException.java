package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * Invitation has been used exception Describes the semantics that the invitation code has been
 * consumed
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationAlreadyUsedException extends ConflictException {

  public InvitationAlreadyUsedException() {
    super(ErrorType.INVITATION_ALREADY_USED, Map.of(), "The invitation code has been used");
  }

  public InvitationAlreadyUsedException(Throwable cause) {
    super(cause, ErrorType.INVITATION_ALREADY_USED, Map.of(), "The invitation code has been used");
  }
}
