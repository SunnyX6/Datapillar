package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * 邀请已使用异常
 * 描述邀请码已被消费语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationAlreadyUsedException extends ConflictException {

    public InvitationAlreadyUsedException() {
        super(ErrorType.INVITATION_ALREADY_USED, Map.of(), "邀请码已被使用");
    }

    public InvitationAlreadyUsedException(Throwable cause) {
        super(cause, ErrorType.INVITATION_ALREADY_USED, Map.of(), "邀请码已被使用");
    }
}
