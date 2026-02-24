package com.sunny.datapillar.studio.module.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private TenantFeaturePermissionMapper tenantFeaturePermissionMapper;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(new TenantContext(10L, "tenant-10", null, null, false));
        roleService = new RoleServiceImpl(
                roleMapper,
                permissionMapper,
                featureObjectMapper,
                tenantFeaturePermissionMapper
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
        role.setStatus(1);
        role.setIsBuiltin(0);
        when(roleMapper.selectOne(any())).thenReturn(role);

        RoleDto.MemberItem member = new RoleDto.MemberItem();
        member.setUserId(101L);
        member.setUsername("sunny");
        member.setMemberStatus(1);
        member.setJoinedAt(LocalDateTime.parse("2026-02-01T10:00:00"));
        member.setAssignedAt(LocalDateTime.parse("2026-02-02T12:00:00"));
        when(roleMapper.selectRoleMembers(10L, 3L, 1)).thenReturn(List.of(member));

        RoleDto.MembersResponse response = roleService.getRoleMembers(3L, 1);

        assertEquals(3L, response.getRoleId());
        assertEquals("开发者", response.getRoleName());
        assertEquals("USER", response.getRoleType());
        assertEquals(1, response.getRoleStatus());
        assertEquals(0, response.getRoleBuiltin());
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

        FeatureObjectDto.ObjectPermission governance = new FeatureObjectDto.ObjectPermission();
        governance.setObjectId(100L);
        governance.setParentId(null);
        governance.setObjectName("数据治理");
        governance.setSort(2);
        governance.setPermissionCode("ADMIN");
        governance.setTenantPermissionCode("ADMIN");

        FeatureObjectDto.ObjectPermission metadata = new FeatureObjectDto.ObjectPermission();
        metadata.setObjectId(101L);
        metadata.setParentId(100L);
        metadata.setObjectName("元数据");
        metadata.setSort(1);
        metadata.setPermissionCode("ADMIN");
        metadata.setTenantPermissionCode("READ");

        FeatureObjectDto.ObjectPermission projects = new FeatureObjectDto.ObjectPermission();
        projects.setObjectId(200L);
        projects.setParentId(null);
        projects.setObjectName("项目");
        projects.setSort(1);
        projects.setPermissionCode("READ");
        projects.setTenantPermissionCode("ADMIN");

        when(featureObjectMapper.selectRoleObjectPermissionsAll(10L, 5L))
                .thenReturn(List.of(governance, metadata, projects));

        List<FeatureObjectDto.ObjectPermission> permissions = roleService.getRolePermissions(5L, "ALL");

        assertEquals(2, permissions.size());
        assertEquals(200L, permissions.get(0).getObjectId());
        assertEquals(100L, permissions.get(1).getObjectId());
        assertEquals(1, permissions.get(1).getChildren().size());
        assertEquals(101L, permissions.get(1).getChildren().get(0).getObjectId());
        assertEquals("READ", permissions.get(1).getChildren().get(0).getPermissionCode());
    }

    private Permission permission(Long id, String code, Integer level) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setLevel(level);
        return permission;
    }
}
