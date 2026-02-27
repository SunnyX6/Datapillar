package com.sunny.datapillar.studio.exception.user;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * 用户名已存在异常
 * 描述用户名唯一约束冲突语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class UsernameAlreadyExistsException extends AlreadyExistsException {

    private static final String TYPE = "USERNAME_ALREADY_EXISTS";

    public UsernameAlreadyExistsException() {
        super(TYPE, Map.of(), "用户名已存在");
    }

    public UsernameAlreadyExistsException(Throwable cause) {
        super(cause, TYPE, Map.of(), "用户名已存在");
    }
}
