package com.sunny.datapillar.auth.dto.login.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "AuthLoginRequest")
public class LoginRequest {

    @NotBlank(message = "stage 不能为空")
    private String stage;

    private Boolean rememberMe;

    private String loginAlias;

    private String password;

    private String tenantCode;

    private String provider;

    private String code;

    private String state;

    private Long tenantId;
}
