package com.sunny.datapillar.studio.module.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import com.sunny.datapillar.studio.dto.user.request.RoleCreateRequest;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRolePrivilegeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRoleService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserRoleService;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectCategoryMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

  @Mock private RoleMapper roleMapper;
  @Mock private PermissionMapper permissionMapper;
  @Mock private FeatureObjectMapper featureObjectMapper;
  @Mock private FeatureObjectCategoryMapper featureObjectCategoryMapper;
  @Mock private TenantFeaturePermissionMapper tenantFeaturePermissionMapper;
  @Mock private UserMapper userMapper;
  @Mock private GravitinoRoleService gravitinoRoleService;
  @Mock private GravitinoRolePrivilegeService gravitinoRolePrivilegeService;
  @Mock private GravitinoUserRoleService gravitinoUserRoleService;

  private RoleServiceImpl roleService;

  @BeforeEach
  void setUp() {
    TenantContextHolder.set(new TenantContext(10L, "tenant-10", null, null, false));
    roleService =
        new RoleServiceImpl(
            roleMapper,
            permissionMapper,
            featureObjectMapper,
            featureObjectCategoryMapper,
            tenantFeaturePermissionMapper,
            userMapper,
            gravitinoRoleService,
            gravitinoRolePrivilegeService,
            gravitinoUserRoleService);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void createRole_shouldPersistAndCreateRemoteRole() {
    RoleCreateRequest request = new RoleCreateRequest();
    request.setName("Developer");
    request.setDescription("Developer role");
    request.setType("group");

    when(roleMapper.findByName(10L, "Developer")).thenReturn(null);
    when(roleMapper.selectMaxSortByTenant(10L)).thenReturn(4);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(301L);
              return 1;
            });

    Long roleId = roleService.createRole(request);

    assertEquals(301L, roleId);
    verify(gravitinoRoleService).createRole("Developer", null);
  }

  @Test
  void deleteRole_shouldDeleteRemoteRoleAndLocalBindings() {
    Role role = new Role();
    role.setId(301L);
    role.setTenantId(10L);
    role.setName("Developer");
    role.setLevel(100);

    when(roleMapper.selectOne(any())).thenReturn(role);
    when(roleMapper.countUsersByRoleId(10L, 301L)).thenReturn(0L);

    roleService.deleteRole(301L);

    verify(gravitinoRoleService).deleteRole("Developer", null);
    verify(roleMapper).deleteRolePermissions(10L, 301L);
    verify(roleMapper).deleteUserRolesByRoleId(10L, 301L);
    verify(roleMapper).deleteById(301L);
  }

  @Test
  void removeRoleMembers_shouldSyncRemainingRemoteRoles() {
    Role role = new Role();
    role.setId(7L);
    role.setTenantId(10L);
    when(roleMapper.selectOne(any())).thenReturn(role);
    when(userMapper.selectUserIdsByMaxLevel(10L, List.of(101L), 0)).thenReturn(List.of());

    User user = new User();
    user.setId(101L);
    user.setTenantId(10L);
    user.setUsername("sunny");
    when(userMapper.selectByIdAndTenantId(10L, 101L)).thenReturn(user);

    Role remainingRole = new Role();
    remainingRole.setName("Analyst");
    when(roleMapper.findByUserId(10L, 101L)).thenReturn(List.of(remainingRole));

    roleService.removeRoleMembers(7L, List.of(101L));

    verify(roleMapper).deleteRoleMembersByUserIds(10L, 7L, List.of(101L));
    verify(gravitinoUserRoleService).replaceUserRoles("sunny", List.of("Analyst"), null);
  }

  @Test
  void removeRoleMembers_shouldRejectPlatformSuperAdmin() {
    Role role = new Role();
    role.setId(7L);
    role.setTenantId(10L);
    when(roleMapper.selectOne(any())).thenReturn(role);
    when(userMapper.selectUserIdsByMaxLevel(10L, List.of(101L), 0)).thenReturn(List.of(101L));

    assertThrows(ForbiddenException.class, () -> roleService.removeRoleMembers(7L, List.of(101L)));
  }

  @Test
  void replaceRoleDataPrivileges_shouldDelegateToGravitinoRolePrivilegeService() {
    Role role = new Role();
    role.setId(301L);
    role.setTenantId(10L);
    role.setName("Developer");
    role.setLevel(100);
    when(roleMapper.selectOne(any())).thenReturn(role);

    RoleDataPrivilegeCommandItem command = new RoleDataPrivilegeCommandItem();
    command.setObjectType("TABLE");
    command.setObjectName("ods.orders");
    command.setPrivilegeCodes(List.of("SELECT"));

    roleService.replaceRoleDataPrivileges(301L, "metadata", List.of(command));

    verify(gravitinoRolePrivilegeService)
        .replaceRoleDataPrivileges(eq("Developer"), eq("metadata"), eq(List.of(command)), eq(null));
  }

  @Test
  void getRoleDataPrivileges_shouldDelegateToGravitinoRolePrivilegeService() {
    Role role = new Role();
    role.setId(301L);
    role.setTenantId(10L);
    role.setName("Developer");
    when(roleMapper.selectOne(any())).thenReturn(role);

    RoleDataPrivilegeItem item = new RoleDataPrivilegeItem();
    item.setDomain("metadata");
    item.setObjectType("TABLE");
    item.setObjectName("ods.orders");
    item.setPrivilegeCode("SELECT");
    when(gravitinoRolePrivilegeService.getRoleDataPrivileges("Developer", "metadata", null))
        .thenReturn(List.of(item));

    List<RoleDataPrivilegeItem> result = roleService.getRoleDataPrivileges(301L, "metadata");

    assertEquals(1, result.size());
    assertEquals("ods.orders", result.getFirst().getObjectName());
  }
}
