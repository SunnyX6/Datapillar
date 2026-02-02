package com.sunny.datapillar.admin.module.user.service;

import java.util.List;

import com.sunny.datapillar.admin.module.user.dto.MenuDto;

/**
 * 菜单服务接口
 *
 * @author sunny
 */
public interface MenuService {

    /**
     * 根据用户ID查询可访问的菜单列表
     */
    List<MenuDto.Response> getMenusByUserId(Long userId, String location);

    /**
     * 查询所有可见菜单
     */
    List<MenuDto.Response> getAllVisibleMenus();

    /**
     * 构建菜单树
     */
    List<MenuDto.Response> buildMenuTree(List<MenuDto.Response> menus);
}
