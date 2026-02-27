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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private FeatureObjectMapper featureObjectMapper;
    @Mock
    private FeatureObjectCategoryMapper featureObjectCategoryMapper;
    @Mock
    private TenantFeaturePermissionMapper tenantFeaturePermissionMapper;
    @Mock
    private UserMapper userMapper;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(new TenantContext(10L, "tenant-10", null, null, false));
        roleService = new RoleServiceImpl(
                roleMapper,
                permissionMapper,
                featureObjectMapper,
                featureObjectCategoryMapper,
                tenantFeaturePermissionMapper,
                userMapper
        );
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
        role.setName("开发者");
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
        assertEquals("开发者", response.getRoleName());
        assertEquals("USER", response.getRoleType());
        assertEquals(100, response.getRoleLevel());
        assertEquals(1, response.getRoleStatus());
        assertEquals(1L, response.getMemberCount());
        assertEquals(1, response.getMembers().size());
        assertEquals(101L, response.getMembers().get(0).getUserId());
        verify(roleMapper).selectRoleMembers(10L, 3L, 1);
    }

    @Test
    void getRoleMembers_shouldRejectWhenRoleNotFound() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        NotFoundException exception = assertThrows(NotFoundException.class, () -> roleService.getRoleMembers(99L, null));

        assertTrue(exception.getMessage().contains("角色不存在"));
        verify(roleMapper, never()).selectRoleMembers(any(), any(), any());
    }

    @Test
    void removeRoleMembers_shouldBatchDeleteUserRoleRelations() {
        Role role = new Role();
        role.setId(7L);
        role.setTenantId(10L);
        when(userMapper.selectUserIdsByMaxLevel(10L, List.of(101L, 102L), 0)).thenReturn(List.of());
        when(roleMapper.selectOne(any())).thenReturn(role);

        roleService.removeRoleMembers(7L, List.of(101L, 101L, 102L));

        ArgumentCaptor<List<Long>> userIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleMapper).deleteRoleMembersByUserIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(7L),
                userIdsCaptor.capture()
        );
        assertEquals(List.of(101L, 102L), userIdsCaptor.getValue());
    }

    @Test
    void removeRoleMembers_shouldIgnoreWhenUserIdsEmpty() {
        Role role = new Role();
        role.setId(7L);
        role.setTenantId(10L);
        when(roleMapper.selectOne(any())).thenReturn(role);

        roleService.removeRoleMembers(7L, List.of());

        verify(roleMapper, never()).deleteRoleMembersByUserIds(any(), any(), any());
    }

    @Test
    void removeRoleMembers_shouldRejectWhenContainsPlatformSuperAdminUser() {
        Role role = new Role();
        role.setId(7L);
        role.setTenantId(10L);
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(userMapper.selectUserIdsByMaxLevel(10L, List.of(101L, 102L), 0)).thenReturn(List.of(101L));

        ForbiddenException exception =
                assertThrows(ForbiddenException.class, () -> roleService.removeRoleMembers(7L, List.of(101L, 102L)));

        assertTrue(exception.getMessage().contains("平台超管用户不允许移除角色"));
        verify(roleMapper, never()).deleteRoleMembersByUserIds(any(), any(), any());
    }

    @Test
    void removeRoleMembers_shouldRejectWhenRoleNotFound() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        NotFoundException exception =
                assertThrows(NotFoundException.class, () -> roleService.removeRoleMembers(88L, List.of(101L)));

        assertTrue(exception.getMessage().contains("角色不存在"));
        verify(roleMapper, never()).deleteRoleMembersByUserIds(any(), any(), any());
    }

    @Test
    void getRolePermissions_shouldBuildTreeAndApplyTenantLimit() {
        Role role = new Role();
        role.setId(5L);
        role.setTenantId(10L);
        when(roleMapper.selectOne(any())).thenReturn(role);

        when(permissionMapper.selectSystemPermissions()).thenReturn(
                List.of(
                        permission(1L, "DISABLE", 0),
                        permission(2L, "READ", 1),
                        permission(3L, "ADMIN", 2)
                )
        );

        FeatureObjectPermissionItem governance = new FeatureObjectPermissionItem();
        governance.setObjectId(100L);
        governance.setParentId(null);
        governance.setObjectName("数据治理");
        governance.setSort(2);
        governance.setPermissionCode("ADMIN");
        governance.setTenantPermissionCode("ADMIN");

        FeatureObjectPermissionItem metadata = new FeatureObjectPermissionItem();
        metadata.setObjectId(101L);
        metadata.setParentId(100L);
        metadata.setObjectName("元数据");
        metadata.setSort(1);
        metadata.setPermissionCode("ADMIN");
        metadata.setTenantPermissionCode("READ");

        FeatureObjectPermissionItem projects = new FeatureObjectPermissionItem();
        projects.setObjectId(200L);
        projects.setParentId(null);
        projects.setObjectName("项目");
        projects.setSort(1);
        projects.setPermissionCode("READ");
        projects.setTenantPermissionCode("ADMIN");

        when(featureObjectMapper.selectRoleObjectPermissionsAll(10L, 5L))
                .thenReturn(List.of(governance, metadata, projects));

        List<FeatureObjectPermissionItem> permissions = roleService.getRolePermissions(5L, "ALL");

        assertEquals(2, permissions.size());
        assertEquals(200L, permissions.get(0).getObjectId());
        assertEquals(100L, permissions.get(1).getObjectId());
        assertEquals(1, permissions.get(1).getChildren().size());
        assertEquals(101L, permissions.get(1).getChildren().get(0).getObjectId());
        assertEquals("READ", permissions.get(1).getChildren().get(0).getPermissionCode());
    }

    @Test
    void createRole_shouldAssignDefaultReadPermissionsForUserRole() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("普通成员");
        request.setDescription("默认权限验证");
        request.setType("USER");

        when(roleMapper.findByName(10L, "普通成员")).thenReturn(null);
        when(roleMapper.selectMaxSortByTenant(10L)).thenReturn(3);
        when(roleMapper.insert(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(9L);
            return 1;
        });

        Permission readPermission = permission(2L, "READ", 1);
        when(permissionMapper.selectSystemByCode("READ")).thenReturn(readPermission);

        FeatureObjectCategory category = new FeatureObjectCategory();
        category.setId(100L);
        category.setCode("MANAGE_DEFINE");
        when(featureObjectCategoryMapper.selectByCode("MANAGE_DEFINE")).thenReturn(category);

        FeatureObject workflow = featureObject(1000L, "/workflow", 200L);
        FeatureObject profile = featureObject(1001L, "/profile", 100L);
        FeatureObject profilePermission = featureObject(1002L, "/profile/permission", 100L);
        FeatureObject profileAi = featureObject(1003L, "/profile/llm/models", 100L);
        FeatureObject home = featureObject(1004L, "/home", 100L);
        when(featureObjectMapper.selectList(any())).thenReturn(
                List.of(workflow, profile, profilePermission, profileAi, home)
        );

        TenantFeaturePermissionLimitItem workflowLimit = permissionLimit(1000L, 1, 1);
        TenantFeaturePermissionLimitItem profileLimit = permissionLimit(1001L, 1, 1);
        TenantFeaturePermissionLimitItem profilePermissionLimit = permissionLimit(1002L, 1, 1);
        TenantFeaturePermissionLimitItem profileAiLimit = permissionLimit(1003L, 1, 1);
        TenantFeaturePermissionLimitItem homeLimit = permissionLimit(1004L, 1, 1);
        when(tenantFeaturePermissionMapper.selectPermissionLimits(10L)).thenReturn(
                List.of(workflowLimit, profileLimit, profilePermissionLimit, profileAiLimit, homeLimit)
        );

        Long roleId = roleService.createRole(request);

        assertEquals(9L, roleId);

        ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
        verify(roleMapper, times(2)).insertRolePermission(captor.capture());
        List<RolePermission> insertedPermissions = captor.getAllValues();
        assertEquals(2, insertedPermissions.size());
        assertTrue(insertedPermissions.stream().anyMatch(item ->
                item.getRoleId().equals(9L)
                        && item.getObjectId().equals(1000L)
                        && item.getPermissionId().equals(2L)));
        assertTrue(insertedPermissions.stream().anyMatch(item ->
                item.getRoleId().equals(9L)
                        && item.getObjectId().equals(1001L)
                        && item.getPermissionId().equals(2L)));
    }

    @Test
    void createRole_shouldNotAssignDefaultPermissionsForAdminRole() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("租户管理员");
        request.setType("ADMIN");

        when(roleMapper.findByName(10L, "租户管理员")).thenReturn(null);
        when(roleMapper.selectMaxSortByTenant(10L)).thenReturn(3);
        when(roleMapper.insert(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(11L);
            return 1;
        });

        Long roleId = roleService.createRole(request);

        assertEquals(11L, roleId);
        verify(permissionMapper, never()).selectSystemByCode(any());
        verify(roleMapper, never()).insertRolePermission(any());
    }

    @Test
    void deleteRole_shouldDeleteRoleChildrenBeforeRoleRecord() {
        Role role = new Role();
        role.setId(3L);
        role.setTenantId(10L);
        role.setLevel(100);
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(roleMapper.countUsersByRoleId(10L, 3L)).thenReturn(0L);

        roleService.deleteRole(3L);

        InOrder inOrder = inOrder(roleMapper);
        inOrder.verify(roleMapper).deleteRolePermissions(10L, 3L);
        inOrder.verify(roleMapper).deleteUserRolesByRoleId(10L, 3L);
        inOrder.verify(roleMapper).deleteById(3L);
    }

    private FeatureObject featureObject(Long id, String path, Long categoryId) {
        FeatureObject object = new FeatureObject();
        object.setId(id);
        object.setPath(path);
        object.setCategoryId(categoryId);
        object.setStatus(1);
        return object;
    }

    private TenantFeaturePermissionLimitItem permissionLimit(Long objectId, Integer status, Integer permissionLevel) {
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
