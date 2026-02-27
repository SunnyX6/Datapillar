package com.sunny.datapillar.auth.dto.login.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthTenantOption")
public class TenantOptionItem {

    private Long tenantId;

    private String tenantCode;

    private String tenantName;

    private Integer status;

    private Integer isDefault;
}
