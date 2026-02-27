package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "FeatureObjectAssignment")
public class RoleFeatureAssignmentItem {

    private Long objectId;

    private Long permissionId;

    private String permissionCode;
}
