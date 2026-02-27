package com.sunny.datapillar.auth.service.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 用户AccessReader组件
 * 负责用户AccessReader核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class UserAccessReader {

    private final UserMapper userMapper;

    /**
     * 构建登录成功响应所需的角色与菜单信息。
     */
    public LoginResponse buildLoginResponse(Long tenantId, User user) {
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setUserId(user.getId());
        loginResponse.setTenantId(tenantId);
        loginResponse.setUsername(user.getUsername());
        loginResponse.setEmail(user.getEmail());
        loginResponse.setRoles(loadRoles(tenantId, user.getId()));
        loginResponse.setMenus(loadMenus(tenantId, user.getId()));
        return loginResponse;
    }

    /**
     * 读取 token claims 需要的角色类型。
     */
    public List<String> loadRoleTypes(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return new ArrayList<>();
        }
        List<RoleItem> roles = loadRoles(tenantId, userId);
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> roleTypes = new ArrayList<>();
        for (RoleItem role : roles) {
            if (role == null || role.getType() == null || role.getType().isBlank()) {
                continue;
            }
            roleTypes.add(role.getType().trim().toUpperCase(Locale.ROOT));
        }
        return roleTypes;
    }

    private List<RoleItem> loadRoles(Long tenantId, Long userId) {
        List<RoleItem> roles = userMapper.selectRolesByUserId(tenantId, userId);
        return roles == null ? new ArrayList<>() : roles;
    }

    private List<MenuItem> loadMenus(Long tenantId, Long userId) {
        List<Map<String, Object>> menuMaps = userMapper.selectMenusByUserId(tenantId, userId);
        return buildMenuTree(menuMaps);
    }

    private List<MenuItem> buildMenuTree(List<Map<String, Object>> menuMaps) {
        if (menuMaps == null || menuMaps.isEmpty()) {
            return new ArrayList<>();
        }

        List<MenuItem> allMenus = new ArrayList<>();
        for (Map<String, Object> map : menuMaps) {
            MenuItem menu = new MenuItem();
            menu.setId(((Number) map.get("id")).longValue());
            menu.setName((String) map.get("name"));
            menu.setPath((String) map.get("path"));
            menu.setPermissionCode((String) map.get("permission_code"));
            menu.setLocation((String) map.get("location"));
            Object categoryId = map.get("category_id");
            if (categoryId instanceof Number) {
                menu.setCategoryId(((Number) categoryId).longValue());
            }
            menu.setCategoryName((String) map.get("category_name"));
            menu.setChildren(new ArrayList<>());
            allMenus.add(menu);
        }

        Map<Long, MenuItem> menuIndex = new HashMap<>();
        for (MenuItem menu : allMenus) {
            menuIndex.put(menu.getId(), menu);
        }

        List<MenuItem> rootMenus = new ArrayList<>();
        for (int i = 0; i < menuMaps.size(); i++) {
            Map<String, Object> map = menuMaps.get(i);
            MenuItem menu = allMenus.get(i);

            Object parentIdObj = map.get("parent_id");
            if (parentIdObj == null) {
                rootMenus.add(menu);
                continue;
            }

            Long parentId = ((Number) parentIdObj).longValue();
            MenuItem parent = menuIndex.get(parentId);
            if (parent != null) {
                parent.getChildren().add(menu);
            } else {
                rootMenus.add(menu);
            }
        }

        return rootMenus;
    }
}
