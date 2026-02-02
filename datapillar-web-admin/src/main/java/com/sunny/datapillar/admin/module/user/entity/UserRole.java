package com.sunny.datapillar.admin.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户角色关联实体类
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
@TableName("user_roles")
public class UserRole {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("role_id")
    private Long roleId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
