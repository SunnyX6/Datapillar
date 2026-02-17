package com.sunny.datapillar.studio.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目组件
 * 负责项目核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("projects")
public class Project {

    /**
     * 项目ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 项目名称
     */
    @TableField("name")
    private String name;

    /**
     * 项目描述
     */
    @TableField("description")
    private String description;

    /**
     * 项目所有者ID
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 项目状态
     */
    @TableField("status")
    private ProjectStatus status;

    /**
     * 项目标签（JSON格式存储）
     */
    @TableField("tags")
    private String tags;

    /**
     * 是否收藏
     */
    @TableField("is_favorite")
    private Boolean isFavorite;

    /**
     * 是否可见
     */
    @TableField("is_visible")
    private Boolean isVisible;

    /**
     * 成员数量
     */
    @TableField("member_count")
    private Integer memberCount;

    /**
     * 最后访问时间
     */
    @TableField("last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标志
     */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;
}
