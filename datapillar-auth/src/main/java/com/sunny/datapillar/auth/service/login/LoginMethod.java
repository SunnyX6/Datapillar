package com.sunny.datapillar.auth.service.login;

/**
 * 登录Method接口
 * 定义登录Method能力契约与行为边界
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LoginMethod {

    String method();

    LoginSubject authenticate(LoginCommand command);
}
