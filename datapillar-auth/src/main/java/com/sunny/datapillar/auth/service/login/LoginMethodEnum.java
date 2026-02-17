package com.sunny.datapillar.auth.service.login;

/**
 * 登录Method枚举
 * 定义登录Method枚举取值与业务语义
 *
 * @author Sunny
 * @date 2026-01-01
 */
public enum LoginMethodEnum {
    PASSWORD("password"),
    SSO("sso");

    private final String key;

    LoginMethodEnum(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
