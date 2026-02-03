package com.sunny.datapillar.platform.module.features.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 功能对象（菜单/页面）实体类
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
@TableName("feature_objects")
public class FeatureObject {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("parent_id")
    private Long parentId;

    private String type;

    private String name;

    @TableField("category_id")
    private Long categoryId;

    private String path;

    private String location;

    private String description;

    private Integer sort;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String categoryName;
}
