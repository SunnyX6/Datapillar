package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * 邀请已失效异常
 * 描述邀请码状态无效语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationInactiveException extends ConflictException {

    public InvitationInactiveException() {
        super(ErrorType.INVITATION_INACTIVE, Map.of(), "邀请码已失效");
    }

    public InvitationInactiveException(Throwable cause) {
        super(cause, ErrorType.INVITATION_INACTIVE, Map.of(), "邀请码已失效");
    }
}
