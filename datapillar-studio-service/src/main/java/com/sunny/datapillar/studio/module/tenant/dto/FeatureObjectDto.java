package com.sunny.datapillar.studio.module.tenant.dto;

import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能Object数据传输对象
 * 定义功能Object数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class FeatureObjectDto {

    @Data
    @Schema(name = "FeatureObjectAssignment")
    public static class Assignment {
        private Long objectId;
        private Long permissionId;
        private String permissionCode;
    }

    @Data
    @Schema(name = "FeatureObjectAssignmentRequest")
    public static class AssignmentRequest {
        private List<Assignment> permissions;
    }

    @Data
    @Schema(name = "FeatureObjectObjectPermission")
    public static class ObjectPermission {
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
        private List<ObjectPermission> children;
    }

    @Data
    @Schema(name = "FeatureObjectRoleSource")
    public static class RoleSource {
        private Long objectId;
        private Long roleId;
        private String roleName;
        private String permissionCode;
    }

    @Data
    @Schema(name = "FeatureObjectTreeNode")
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
