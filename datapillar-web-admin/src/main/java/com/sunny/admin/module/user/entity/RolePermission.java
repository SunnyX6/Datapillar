package com.sunny.admin.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体类
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Data
@TableName("role_permissions")
public class RolePermission {
    /**
     * 角色ID
     */
    @TableField("role_id")
    private Long roleId;
    
    /**
     * 权限ID
     */
    @TableField("permission_id")
    private Long permissionId;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}