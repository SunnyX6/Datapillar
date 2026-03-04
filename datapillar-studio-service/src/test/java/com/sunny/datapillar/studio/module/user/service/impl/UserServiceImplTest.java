package com.sunny.datapillar.studio.module.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @Mock private UserMapper userMapper;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PermissionMapper permissionMapper;
  @Mock private FeatureObjectMapper featureObjectMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private TenantMapper tenantMapper;

  private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    TenantContextHolder.set(new TenantContext(10L, "tenant-10", null, null, false));
    userService =
        new UserServiceImpl(
            userMapper,
            tenantUserMapper,
            passwordEncoder,
            permissionMapper,
            featureObjectMapper,
            roleMapper,
            tenantMapper);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void assignRoles_shouldReplaceRoleBindingsWithDistinctIds() {
    User user = new User();
    user.setId(501L);
    user.setTenantId(10L);
    when(userMapper.selectByIdAndTenantId(10L, 501L)).thenReturn(user);

    Role role301 = new Role();
    role301.setId(301L);
    role301.setTenantId(10L);
    Role role302 = new Role();
    role302.setId(302L);
    role302.setTenantId(10L);
    when(roleMapper.selectById(301L)).thenReturn(role301);
    when(roleMapper.selectById(302L)).thenReturn(role302);

    userService.assignRoles(501L, List.of(301L, 301L, 302L));

    verify(userMapper).deleteUserRoles(10L, 501L);
    ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
    verify(userMapper, times(2)).insertUserRole(userRoleCaptor.capture());
    List<Long> roleIds =
        userRoleCaptor.getAllValues().stream()
            .map(UserRole::getRoleId)
            .collect(Collectors.toList());
    assertEquals(List.of(301L, 302L), roleIds);
  }

  @Test
  void assignRoles_shouldRejectRoleOutsideTenant() {
    User user = new User();
    user.setId(501L);
    user.setTenantId(10L);
    when(userMapper.selectByIdAndTenantId(10L, 501L)).thenReturn(user);

    Role invalidRole = new Role();
    invalidRole.setId(301L);
    invalidRole.setTenantId(99L);
    when(roleMapper.selectById(301L)).thenReturn(invalidRole);

    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> userService.assignRoles(501L, List.of(301L)));

    assertEquals("Parameter error", exception.getMessage());
  }

  @Test
  void deleteUser_shouldRemoveTenantMemberAndRoles() {
    User user = new User();
    user.setId(501L);
    user.setTenantId(10L);
    user.setLevel(100);
    when(userMapper.selectByIdAndTenantId(10L, 501L)).thenReturn(user);

    userService.deleteUser(501L);

    verify(tenantUserMapper).deleteByTenantIdAndUserId(10L, 501L);
    verify(userMapper).deleteUserRoles(10L, 501L);
  }

  @Test
  void deleteUser_shouldRejectPlatformSuperAdmin() {
    User user = new User();
    user.setId(1L);
    user.setTenantId(10L);
    user.setLevel(0);
    when(userMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(user);

    ForbiddenException exception =
        assertThrows(ForbiddenException.class, () -> userService.deleteUser(1L));

    assertEquals("Platform super-managed users are not allowed to delete", exception.getMessage());
  }
}
