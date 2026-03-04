package com.sunny.datapillar.auth.service.login;

/**
 * Contract for login methods.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LoginMethod {

  String method();

  LoginSubject authenticate(LoginCommand command);
}
