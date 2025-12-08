package com.sunny.admin.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单实体类
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Data
@TableName("menus")
public class Menu {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 父菜单ID
     */
    @TableField("parent_id")
    private Long parentId;
    
    /**
     * 菜单名称
     */
    private String name;
    
    /**
     * 菜单路径
     */
    private String path;
    
    /**
     * 组件路径
     */
    private String component;
    
    /**
     * 菜单图标
     */
    private String icon;
    
    /**
     * 权限代码
     */
    @TableField("permission_code")
    private String permissionCode;
    
    /**
     * 是否可见
     */
    private Integer visible;
    
    /**
     * 排序
     */
    private Integer sort;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}