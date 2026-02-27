package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "FeatureObjectRoleSource")
public class FeatureRoleSourceItem {

    private Long objectId;

    private Long roleId;

    private String roleName;

    private String permissionCode;
}
