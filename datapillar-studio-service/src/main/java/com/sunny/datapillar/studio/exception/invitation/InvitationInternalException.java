package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;

/**
 * 邀请内部错误异常
 * 描述邀请流程不可恢复系统错误语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationInternalException extends InternalException {

    public InvitationInternalException() {
        super(ErrorType.INVITATION_INTERNAL_ERROR, Map.of(), "邀请处理失败");
    }

    public InvitationInternalException(Throwable cause) {
        super(cause, ErrorType.INVITATION_INTERNAL_ERROR, Map.of(), "邀请处理失败");
    }
}
