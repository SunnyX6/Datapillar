package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "TenantCreate")
public class TenantCreateRequest {

    @NotBlank(message = "租户编码不能为空")
    @Size(max = 64, message = "租户编码长度不能超过64个字符")
    private String code;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称长度不能超过128个字符")
    private String name;

    @NotBlank(message = "租户类型不能为空")
    @Size(max = 32, message = "租户类型长度不能超过32个字符")
    private String type;
}
