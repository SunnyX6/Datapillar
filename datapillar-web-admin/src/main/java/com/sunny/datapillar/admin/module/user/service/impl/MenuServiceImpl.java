package com.sunny.datapillar.admin.module.user.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.sunny.datapillar.admin.module.user.dto.MenuDto;
import com.sunny.datapillar.admin.module.user.entity.Menu;
import com.sunny.datapillar.admin.module.user.mapper.MenuMapper;
import com.sunny.datapillar.admin.module.user.service.MenuService;

import lombok.RequiredArgsConstructor;

/**
 * 菜单服务实现类
 *
 * @author sunny
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;

    @Override
    public List<MenuDto.Response> getMenusByUserId(Long userId, String location) {
        List<Menu> menus = menuMapper.findByUserId(userId, location);
        List<MenuDto.Response> menuResponses = convertToMenuResponses(menus);
        return buildMenuTree(menuResponses);
    }

    @Override
    public List<MenuDto.Response> getAllVisibleMenus() {
        List<Menu> menus = menuMapper.findAllVisible();
        List<MenuDto.Response> menuResponses = convertToMenuResponses(menus);
        return buildMenuTree(menuResponses);
    }

    @Override
    public List<MenuDto.Response> buildMenuTree(List<MenuDto.Response> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<MenuDto.Response>> menuMap = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));

        List<MenuDto.Response> rootMenus = menuMap.getOrDefault(0L, new ArrayList<>());
        buildChildren(rootMenus, menuMap);

        rootMenus.sort((m1, m2) -> {
            int sort1 = m1.getSort() != null ? m1.getSort() : 0;
            int sort2 = m2.getSort() != null ? m2.getSort() : 0;
            return Integer.compare(sort1, sort2);
        });

        return rootMenus;
    }

    private void buildChildren(List<MenuDto.Response> parentMenus, Map<Long, List<MenuDto.Response>> menuMap) {
        for (MenuDto.Response parentMenu : parentMenus) {
            List<MenuDto.Response> children = menuMap.getOrDefault(parentMenu.getId(), new ArrayList<>());
            if (!children.isEmpty()) {
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

    private List<MenuDto.Response> convertToMenuResponses(List<Menu> menus) {
        return menus.stream()
                .map(this::convertToMenuResponse)
                .collect(Collectors.toList());
    }

    private MenuDto.Response convertToMenuResponse(Menu menu) {
        MenuDto.Response response = new MenuDto.Response();
        BeanUtils.copyProperties(menu, response);
        return response;
    }
}
