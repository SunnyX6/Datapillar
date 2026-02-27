package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "FeatureEntitlementUpdateItem")
public class TenantFeatureUpdateItem {

    private Long objectId;

    private Long permissionId;

    private String permissionCode;

    private Integer status;
}
