package com.sunny.datapillar.studio.module.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceImplTest {

  @Mock private UserService userService;

  @Test
  void listMenus_shouldFilterDisabledAndBuildTree() {
    FeatureObjectPermissionItem root =
        permissionItem(1L, null, "Projects", "/projects", "MENU", "READ");
    FeatureObjectPermissionItem child =
        permissionItem(2L, 1L, "Workflow", "/workflow", "MENU", "ADMIN");
    FeatureObjectPermissionItem page =
        permissionItem(3L, null, "Workflow page", "/workflow/page", "PAGE", "READ");
    FeatureObjectPermissionItem disabled =
        permissionItem(4L, null, "Profile", "/profile", "MENU", "DISABLE");
    FeatureObjectPermissionItem orphan =
        permissionItem(5L, 99L, "Governance", "/governance", "MENU", "READ");

    when(userService.getUserPermissions(100L))
        .thenReturn(List.of(root, child, page, disabled, orphan));

    UserPermissionServiceImpl service = new UserPermissionServiceImpl(userService);
    List<UserMenuItem> menus = service.listMenus(100L);

    assertEquals(2, menus.size());
    assertEquals(1L, menus.get(0).getId());
    assertEquals("/projects", menus.get(0).getPath());
    assertEquals(1, menus.get(0).getChildren().size());
    assertEquals(2L, menus.get(0).getChildren().get(0).getId());

    assertEquals(5L, menus.get(1).getId());
    assertTrue(menus.get(1).getChildren().isEmpty());
  }

  @Test
  void listMenus_shouldReturnEmptyWhenNoPermissions() {
    when(userService.getUserPermissions(100L)).thenReturn(List.of());

    UserPermissionServiceImpl service = new UserPermissionServiceImpl(userService);
    List<UserMenuItem> menus = service.listMenus(100L);

    assertTrue(menus.isEmpty());
  }

  private FeatureObjectPermissionItem permissionItem(
      Long objectId, Long parentId, String name, String path, String type, String permissionCode) {
    FeatureObjectPermissionItem item = new FeatureObjectPermissionItem();
    item.setObjectId(objectId);
    item.setParentId(parentId);
    item.setObjectName(name);
    item.setObjectPath(path);
    item.setObjectType(type);
    item.setPermissionCode(permissionCode);
    return item;
  }
}
