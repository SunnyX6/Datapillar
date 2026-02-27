package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "FeatureEntitlementPermissionLimit")
public class TenantFeaturePermissionLimitItem {

    private Long objectId;

    private Integer status;

    private String permissionCode;

    private Integer permissionLevel;
}
