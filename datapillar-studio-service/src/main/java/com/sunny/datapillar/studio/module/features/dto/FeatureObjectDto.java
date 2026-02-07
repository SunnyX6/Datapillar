package com.sunny.datapillar.studio.module.features.dto;

import java.util.List;
import lombok.Data;

/**
 * 功能对象 DTO
 *
 * @author sunny
 */
public class FeatureObjectDto {

    @Data
    public static class Assignment {
        private Long objectId;
        private Long permissionId;
        private String permissionCode;
    }

    @Data
    public static class AssignmentRequest {
        private List<Assignment> permissions;
    }

    @Data
    public static class ObjectPermission {
        private Long objectId;
        private String objectName;
        private String objectPath;
        private String objectType;
        private String location;
        private Long categoryId;
        private String categoryName;
        private String permissionCode;
        private String userOverrideCode;
        private String tenantPermissionCode;
        private List<RoleSource> roleSources;
    }

    @Data
    public static class RoleSource {
        private Long objectId;
        private Long roleId;
        private String roleName;
        private String permissionCode;
    }

    @Data
    public static class TreeNode {
        private Long id;
        private Long parentId;
        private String type;
        private String name;
        private String path;
        private String location;
        private Integer sort;
        private Integer status;
        private Long categoryId;
        private String categoryName;
        private List<TreeNode> children;
    }
}
