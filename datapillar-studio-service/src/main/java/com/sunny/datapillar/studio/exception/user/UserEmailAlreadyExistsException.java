package com.sunny.datapillar.studio.exception.user;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * 用户邮箱已存在异常
 * 描述用户邮箱唯一约束冲突语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class UserEmailAlreadyExistsException extends AlreadyExistsException {

    private static final String TYPE = "USER_EMAIL_ALREADY_EXISTS";

    public UserEmailAlreadyExistsException() {
        super(TYPE, Map.of(), "邮箱已存在");
    }

    public UserEmailAlreadyExistsException(Throwable cause) {
        super(cause, TYPE, Map.of(), "邮箱已存在");
    }
}
