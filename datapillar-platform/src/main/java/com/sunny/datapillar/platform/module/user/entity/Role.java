package com.sunny.datapillar.platform.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色实体类
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
@TableName("roles")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String type;

    private String name;

    private String description;

    private Integer status;

    private Integer sort;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
