package com.sunny.datapillar.admin.module.user.dto;

import java.util.List;
import lombok.Data;

/**
 * 权限对象 DTO
 *
 * @author sunny
 */
public class PermissionObjectDto {

    @Data
    public static class Assignment {
        private Long objectId;
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
        private List<RoleSource> roleSources;
    }

    @Data
    public static class RoleSource {
        private Long objectId;
        private Long roleId;
        private String roleName;
        private String permissionCode;
    }
}
