package com.sunny.admin.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单响应DTO
 *
 * @author sunny
 * @since 2024-01-01
 */
@Data
public class MenuRespDto {
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private String permissionCode;
    private Integer visible;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 子菜单列表
     */
    private List<MenuRespDto> children;
}
