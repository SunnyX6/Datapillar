package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "FeatureObjectObjectPermission")
public class FeatureObjectPermissionItem {

    private Long objectId;

    private Long parentId;

    private String objectName;

    private String objectPath;

    private String objectType;

    private String location;

    private Long categoryId;

    private String categoryName;

    private Integer sort;

    private String permissionCode;

    private String tenantPermissionCode;

    private List<FeatureObjectPermissionItem> children;
}
