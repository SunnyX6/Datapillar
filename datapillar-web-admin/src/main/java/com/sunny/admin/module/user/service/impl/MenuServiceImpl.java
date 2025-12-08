package com.sunny.admin.module.user.service.impl;

import com.sunny.admin.module.user.dto.MenuRespDto;
import com.sunny.admin.module.user.entity.Menu;
import com.sunny.admin.module.user.mapper.MenuMapper;
import com.sunny.admin.module.user.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {
    
    private final MenuMapper menuMapper;
    
    @Override
    public List<MenuRespDto> getMenusByUserId(Long userId) {
        List<Menu> menus = menuMapper.findByUserId(userId);
        List<MenuRespDto> menuResponses = convertToMenuRespDtos(menus);
        return buildMenuTree(menuResponses);
    }
    
    @Override
    public List<MenuRespDto> getAllVisibleMenus() {
        List<Menu> menus = menuMapper.findAllVisible();
        List<MenuRespDto> menuResponses = convertToMenuRespDtos(menus);
        return buildMenuTree(menuResponses);
    }
    
    @Override
    public List<MenuRespDto> buildMenuTree(List<MenuRespDto> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 按父ID分组
        Map<Long, List<MenuRespDto>> menuMap = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        
        // 构建树结构
        List<MenuRespDto> rootMenus = menuMap.getOrDefault(0L, new ArrayList<>());
        buildChildren(rootMenus, menuMap);
        
        // 按sort字段排序
        rootMenus.sort((m1, m2) -> {
            int sort1 = m1.getSort() != null ? m1.getSort() : 0;
            int sort2 = m2.getSort() != null ? m2.getSort() : 0;
            return Integer.compare(sort1, sort2);
        });
        
        return rootMenus;
    }
    
    /**
     * 递归构建子菜单
     */
    private void buildChildren(List<MenuRespDto> parentMenus, Map<Long, List<MenuRespDto>> menuMap) {
        for (MenuRespDto parentMenu : parentMenus) {
            List<MenuRespDto> children = menuMap.getOrDefault(parentMenu.getId(), new ArrayList<>());
            if (!children.isEmpty()) {
                // 按sort字段排序
                children.sort((m1, m2) -> {
                    int sort1 = m1.getSort() != null ? m1.getSort() : 0;
                    int sort2 = m2.getSort() != null ? m2.getSort() : 0;
                    return Integer.compare(sort1, sort2);
                });
                parentMenu.setChildren(children);
                buildChildren(children, menuMap);
            }
        }
    }
    
    /**
     * 转换Menu列表为MenuRespDto列表
     */
    private List<MenuRespDto> convertToMenuRespDtos(List<Menu> menus) {
        return menus.stream()
                .map(this::convertToMenuRespDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换Menu为MenuRespDto
     */
    private MenuRespDto convertToMenuRespDto(Menu menu) {
        MenuRespDto response = new MenuRespDto();
        BeanUtils.copyProperties(menu, response);
        return response;
    }
}