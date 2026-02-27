package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ForbiddenException;
import java.util.Map;

/**
 * SSO 身份访问拒绝异常
 * 描述当前用户不具备身份绑定权限语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoIdentityAccessDeniedException extends ForbiddenException {

    public SsoIdentityAccessDeniedException() {
        super(ErrorType.SSO_IDENTITY_ACCESS_DENIED, Map.of(), "无SSO绑定权限");
    }

    public SsoIdentityAccessDeniedException(Throwable cause) {
        super(cause, ErrorType.SSO_IDENTITY_ACCESS_DENIED, Map.of(), "无SSO绑定权限");
    }
}
