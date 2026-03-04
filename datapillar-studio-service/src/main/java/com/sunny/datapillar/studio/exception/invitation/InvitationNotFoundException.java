package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * There is no exception in the invitation Describe invitation code miss semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationNotFoundException extends NotFoundException {

  public InvitationNotFoundException() {
    super(ErrorType.INVITATION_NOT_FOUND, Map.of(), "The invitation code does not exist");
  }

  public InvitationNotFoundException(Throwable cause) {
    super(cause, ErrorType.INVITATION_NOT_FOUND, Map.of(), "The invitation code does not exist");
  }
}
