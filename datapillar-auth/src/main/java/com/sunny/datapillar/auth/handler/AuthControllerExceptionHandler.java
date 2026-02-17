package com.sunny.datapillar.auth.handler;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证Controller异常处理器
 * 负责认证Controller异常处理流程与结果输出
 *
 * @author Sunny
 * @date 2026-01-01
 */
@RestControllerAdvice
public class AuthControllerExceptionHandler extends BaseControllerExceptionHandler {

    @Override
    protected String resolveDuplicateKeyMessage(DuplicateKeyException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "数据已存在";
        }
        if (message.contains("username")) {
            return "用户名已存在";
        }
        if (message.contains("email")) {
            return "邮箱已被注册";
        }
        return "数据已存在，请检查输入内容";
    }
}
