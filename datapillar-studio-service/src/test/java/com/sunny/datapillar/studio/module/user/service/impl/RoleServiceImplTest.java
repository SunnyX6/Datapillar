package com.sunny.datapillar.studio.module.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.connector.runtime.ConnectorKernel;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeSyncRequest;
import com.sunny.datapillar.studio.dto.tenant.response.TenantFeaturePermissionLimitItem;
import com.sunny.datapillar.studio.dto.user.request.RoleCreateRequest;
import com.sunny.datapillar.studio.dto.user.response.RoleMemberItem;
import com.sunny.datapillar.studio.dto.user.response.RoleMembersResponse;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObjectCategory;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectCategoryMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock private ConnectorKernel connectorKernel;

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
            connectorKernel,
            new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void getRoleMembers_shouldReturnRoleScopedResponse() {
    Role role = new Role();
    role.setId(3L);
    role.setTenantId(10L);
    role.setName("Developer");
    role.setType("USER");
    role.setLevel(100);
    role.setStatus(1);
    when(roleMapper.selectOne(any())).thenReturn(role);

    RoleMemberItem member = new RoleMemberItem();
    member.setUserId(101L);
    member.setUsername("sunny");
    member.setMemberStatus(1);
    member.setJoinedAt(LocalDateTime.parse("2026-02-01T10:00:00"));
    member.setAssignedAt(LocalDateTime.parse("2026-02-02T12:00:00"));
    when(roleMapper.selectRoleMembers(10L, 3L, 1)).thenReturn(List.of(member));

    RoleMembersResponse response = roleService.getRoleMembers(3L, 1);

    assertEquals(3L, response.getRoleId());
    assertEquals("Developer", response.getRoleName());
    assertEquals("USER", response.getRoleType());
    assertEquals(100, response.getRoleLevel());
    assertEquals(1, response.getRoleStatus());
    assertEquals(1L, response.getMemberCount());
    assertEquals(1, response.getMembers().size());
  }

  @Test
  void removeRoleMembers_shouldRejectWhenContainsPlatformSuperAdminUser() {
    Role role = new Role();
    role.setId(7L);
    role.setTenantId(10L);
    when(roleMapper.selectOne(any())).thenReturn(role);
    when(userMapper.selectUserIdsByMaxLevel(10L, List.of(101L, 102L), 0)).thenReturn(List.of(101L));

    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class, () -> roleService.removeRoleMembers(7L, List.of(101L, 102L)));

    assertTrue(
        exception.getMessage().contains("Platform super users are not allowed to remove roles"));
    verify(roleMapper, never()).deleteRoleMembersByUserIds(any(), any(), any());
  }

  @Test
  void getRoleMembers_shouldRejectWhenRoleNotFound() {
    when(roleMapper.selectOne(any())).thenReturn(null);

    NotFoundException exception =
        assertThrows(NotFoundException.class, () -> roleService.getRoleMembers(99L, null));

    assertTrue(exception.getMessage().contains("role does not exist"));
    verify(roleMapper, never()).selectRoleMembers(any(), any(), any());
  }

  @Test
  void createRole_shouldAssignDefaultReadPermissionsForUserRole() {
    RoleCreateRequest request = new RoleCreateRequest();
    request.setName("ordinary member");
    request.setDescription("Default permission verification");
    request.setType("USER");

    when(roleMapper.findByName(10L, "ordinary member")).thenReturn(null);
    when(roleMapper.selectMaxSortByTenant(10L)).thenReturn(3);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(9L);
              return 1;
            });

    when(permissionMapper.selectSystemByCode("READ")).thenReturn(permission(2L, "READ", 1));

    FeatureObjectCategory category = new FeatureObjectCategory();
    category.setId(100L);
    category.setCode("MANAGE_DEFINE");
    when(featureObjectCategoryMapper.selectByCode("MANAGE_DEFINE")).thenReturn(category);

    FeatureObject workflow = featureObject(1000L, "/workflow", 200L);
    FeatureObject profile = featureObject(1001L, "/profile", 100L);
    FeatureObject profilePermission = featureObject(1002L, "/profile/permission", 100L);
    FeatureObject profileAi = featureObject(1003L, "/profile/llm/models", 100L);
    FeatureObject home = featureObject(1004L, "/home", 100L);
    when(featureObjectMapper.selectList(any()))
        .thenReturn(List.of(workflow, profile, profilePermission, profileAi, home));

    when(tenantFeaturePermissionMapper.selectPermissionLimits(10L))
        .thenReturn(
            List.of(
                permissionLimit(1000L, 1, 1),
                permissionLimit(1001L, 1, 1),
                permissionLimit(1002L, 1, 1),
                permissionLimit(1003L, 1, 1),
                permissionLimit(1004L, 1, 1)));

    Long roleId = roleService.createRole(request);

    assertEquals(9L, roleId);
    ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
    verify(roleMapper, times(2)).insertRolePermission(captor.capture());
    List<RolePermission> insertedPermissions = captor.getAllValues();
    assertEquals(2, insertedPermissions.size());
    assertTrue(
        insertedPermissions.stream()
            .anyMatch(
                item ->
                    item.getRoleId().equals(9L)
                        && item.getObjectId().equals(1000L)
                        && item.getPermissionId().equals(2L)));
    assertTrue(
        insertedPermissions.stream()
            .anyMatch(
                item ->
                    item.getRoleId().equals(9L)
                        && item.getObjectId().equals(1001L)
                        && item.getPermissionId().equals(2L)));
  }

  @Test
  void roleDataPrivilegeApi_shouldCallConnectorKernel() {
    Role role = new Role();
    role.setId(7L);
    role.setTenantId(10L);
    role.setLevel(100);
    role.setName("Developer");
    when(roleMapper.selectOne(any())).thenReturn(role);
    when(connectorKernel.invoke(any()))
        .thenReturn(ConnectorResponse.of(JsonNodeFactory.instance.objectNode().putArray("items")));

    assertEquals(0, roleService.getRoleDataPrivileges(7L, "SEMANTICS").size());
    roleService.updateRoleDataPrivileges(7L, new RoleDataPrivilegeSyncRequest());
    verify(connectorKernel, times(2)).invoke(any());
  }

  private FeatureObject featureObject(Long id, String path, Long categoryId) {
    FeatureObject object = new FeatureObject();
    object.setId(id);
    object.setPath(path);
    object.setCategoryId(categoryId);
    object.setStatus(1);
    return object;
  }

  private TenantFeaturePermissionLimitItem permissionLimit(
      Long objectId, Integer status, Integer permissionLevel) {
    TenantFeaturePermissionLimitItem limit = new TenantFeaturePermissionLimitItem();
    limit.setObjectId(objectId);
    limit.setStatus(status);
    limit.setPermissionLevel(permissionLevel);
    return limit;
  }

  private Permission permission(Long id, String code, Integer level) {
    Permission permission = new Permission();
    permission.setId(id);
    permission.setCode(code);
    permission.setLevel(level);
    return permission;
  }
}
