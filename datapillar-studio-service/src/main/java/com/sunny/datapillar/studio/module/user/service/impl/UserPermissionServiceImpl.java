package com.sunny.datapillar.studio.module.user.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.sunny.datapillar.studio.module.user.service.UserPermissionService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * User permission service implementation Implement user permission business process and rule
 * verification
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

  private static final String MENU_OBJECT_TYPE = "MENU";
  private static final String DISABLE_PERMISSION = "DISABLE";

  private final UserService userService;

  @Override
  public List<FeatureObjectPermissionItem> listPermissions(Long userId) {
    return userService.getUserPermissions(userId);
  }

  @Override
  public List<UserMenuItem> listMenus(Long userId) {
    List<FeatureObjectPermissionItem> permissions = userService.getUserPermissions(userId);
    return buildMenuTree(permissions);
  }

  private List<UserMenuItem> buildMenuTree(List<FeatureObjectPermissionItem> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return new ArrayList<>();
    }

    Map<Long, UserMenuItem> menuNodeMap = new LinkedHashMap<>();
    Map<Long, Long> parentMap = new LinkedHashMap<>();
    for (FeatureObjectPermissionItem permission : permissions) {
      if (permission == null || permission.getObjectId() == null) {
        continue;
      }
      if (!MENU_OBJECT_TYPE.equalsIgnoreCase(permission.getObjectType())) {
        continue;
      }
      if (isDisabledPermission(permission.getPermissionCode())) {
        continue;
      }
      UserMenuItem menuItem = toMenuItem(permission);
      menuItem.setChildren(new ArrayList<>());
      menuNodeMap.put(permission.getObjectId(), menuItem);
      parentMap.put(permission.getObjectId(), permission.getParentId());
    }

    List<UserMenuItem> roots = new ArrayList<>();
    for (Map.Entry<Long, UserMenuItem> entry : menuNodeMap.entrySet()) {
      Long parentId = parentMap.get(entry.getKey());
      UserMenuItem parent = parentId == null ? null : menuNodeMap.get(parentId);
      if (parent == null) {
        roots.add(entry.getValue());
        continue;
      }
      parent.getChildren().add(entry.getValue());
    }
    return roots;
  }

  private boolean isDisabledPermission(String permissionCode) {
    if (permissionCode == null || permissionCode.isBlank()) {
      return true;
    }
    return DISABLE_PERMISSION.equals(permissionCode.trim().toUpperCase(Locale.ROOT));
  }

  private UserMenuItem toMenuItem(FeatureObjectPermissionItem permission) {
    UserMenuItem item = new UserMenuItem();
    item.setId(permission.getObjectId());
    item.setName(permission.getObjectName());
    item.setPath(permission.getObjectPath());
    item.setPermissionCode(permission.getPermissionCode());
    item.setLocation(permission.getLocation());
    item.setCategoryId(permission.getCategoryId());
    item.setCategoryName(permission.getCategoryName());
    return item;
  }
}
