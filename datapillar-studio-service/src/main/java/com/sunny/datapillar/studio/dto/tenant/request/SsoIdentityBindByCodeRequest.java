package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(name = "SsoIdentityBindByCodeRequest")
public class SsoIdentityBindByCodeRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "provider不能为空")
    @Pattern(regexp = "(?i)^dingtalk$", message = "参数错误")
    private String provider;

    @NotBlank(message = "authCode不能为空")
    private String authCode;
}
