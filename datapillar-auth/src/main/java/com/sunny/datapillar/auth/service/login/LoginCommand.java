package com.sunny.datapillar.auth.service.login;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录Command组件
 * 负责登录Command核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
public class LoginCommand {

    @NotBlank(message = "method 不能为空")
    private String method;

    private Boolean rememberMe;

    private String loginAlias;

    private String password;

    private String tenantCode;

    private String provider;

    private String code;

    private String state;

    @JsonIgnore
    private String clientIp;
}
