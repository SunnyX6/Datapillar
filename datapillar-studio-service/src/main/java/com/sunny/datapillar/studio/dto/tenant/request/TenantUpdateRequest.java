package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "TenantUpdate")
public class TenantUpdateRequest {

    @Size(max = 128, message = "租户名称长度不能超过128个字符")
    private String name;

    @Size(max = 32, message = "租户类型长度不能超过32个字符")
    private String type;
}
