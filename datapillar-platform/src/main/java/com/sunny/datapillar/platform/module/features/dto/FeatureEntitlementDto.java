package com.sunny.datapillar.platform.module.features.dto;

import java.util.List;
import lombok.Data;

/**
 * 租户功能授权 DTO
 */
public class FeatureEntitlementDto {

    @Data
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
        private String permissionCode;
        private Integer permissionLevel;
    }

    @Data
    public static class UpdateItem {
        private Long objectId;
        private String permissionCode;
        private Integer status;
    }

    @Data
    public static class UpdateRequest {
        private List<UpdateItem> items;
    }

    @Data
    public static class PermissionLimit {
        private Long objectId;
        private Integer status;
        private String permissionCode;
        private Integer permissionLevel;
    }
}
