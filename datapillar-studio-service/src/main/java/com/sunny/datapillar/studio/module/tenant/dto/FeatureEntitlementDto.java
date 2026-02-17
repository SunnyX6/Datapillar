package com.sunny.datapillar.studio.module.tenant.dto;

import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能Entitlement数据传输对象
 * 定义功能Entitlement数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class FeatureEntitlementDto {

    @Data
    @Schema(name = "FeatureEntitlementItem")
    public static class Item {
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

    @Data
    @Schema(name = "FeatureEntitlementUpdateItem")
    public static class UpdateItem {
        private Long objectId;
        private Long permissionId;
        private String permissionCode;
        private Integer status;
    }

    @Data
    @Schema(name = "FeatureEntitlementUpdateRequest")
    public static class UpdateRequest {
        private List<UpdateItem> items;
    }

    @Data
    @Schema(name = "FeatureEntitlementPermissionLimit")
    public static class PermissionLimit {
        private Long objectId;
        private Integer status;
        private String permissionCode;
        private Integer permissionLevel;
    }
}
