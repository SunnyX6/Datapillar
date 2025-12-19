package com.sunny.datapillar.admin.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体类
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Data
@TableName("user_roles")
public class UserRole {
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 角色ID
     */
    @TableField("role_id")
    private Long roleId;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}