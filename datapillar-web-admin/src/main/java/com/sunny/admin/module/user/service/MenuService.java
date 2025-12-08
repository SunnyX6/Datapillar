package com.sunny.admin.module.user.service;

import com.sunny.admin.module.user.dto.MenuRespDto;

import java.util.List;

/**
 * 菜单服务接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
public interface MenuService {
    
    /**
     * 根据用户ID查询可访问的菜单列表
     */
    List<MenuRespDto> getMenusByUserId(Long userId);
    
    /**
     * 查询所有可见菜单
     */
    List<MenuRespDto> getAllVisibleMenus();
    
    /**
     * 构建菜单树
     */
    List<MenuRespDto> buildMenuTree(List<MenuRespDto> menus);
}