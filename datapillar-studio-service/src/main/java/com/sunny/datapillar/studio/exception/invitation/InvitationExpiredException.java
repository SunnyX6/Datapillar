package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ConflictException;
import java.util.Map;

/**
 * 邀请已过期异常
 * 描述邀请码超过有效期语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationExpiredException extends ConflictException {

    public InvitationExpiredException() {
        super(ErrorType.INVITATION_EXPIRED, Map.of(), "邀请码已过期");
    }

    public InvitationExpiredException(Throwable cause) {
        super(cause, ErrorType.INVITATION_EXPIRED, Map.of(), "邀请码已过期");
    }
}
