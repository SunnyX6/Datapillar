package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "FeatureEntitlementItem")
public class TenantFeatureItem {

    private Long objectId;

    private String objectName;

    private String objectPath;

    private String objectType;

    private String location;

    private Long categoryId;

    private String categoryName;

    private Integer sort;

    private Integer objectStatus;

    private Integer entitlementStatus;

    private Long permissionId;

    private String permissionCode;

    private Integer permissionLevel;
}
