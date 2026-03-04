package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * Invitation has expired exception Describe the semantics of the invitation code exceeding the
 * validity period
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationExpiredException extends ConflictException {

  public InvitationExpiredException() {
    super(ErrorType.INVITATION_EXPIRED, Map.of(), "The invitation code has expired");
  }

  public InvitationExpiredException(Throwable cause) {
    super(cause, ErrorType.INVITATION_EXPIRED, Map.of(), "The invitation code has expired");
  }
}
