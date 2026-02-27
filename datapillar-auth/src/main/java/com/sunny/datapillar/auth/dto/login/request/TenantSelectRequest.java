package com.sunny.datapillar.auth.dto.login.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "AuthLoginTenantRequest")
public class TenantSelectRequest {

    @NotBlank(message = "loginToken 不能为空")
    private String loginToken;

    @NotNull(message = "tenantId 不能为空")
    private Long tenantId;
}
