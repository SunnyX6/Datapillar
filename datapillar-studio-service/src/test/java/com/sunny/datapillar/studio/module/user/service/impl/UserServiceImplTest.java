package com.sunny.datapillar.studio.module.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import com.sunny.datapillar.studio.dto.user.request.UserCreateRequest;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserDataPrivilegeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserRoleService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserService;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @Mock private UserMapper userMapper;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PermissionMapper permissionMapper;
  @Mock private FeatureObjectMapper featureObjectMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private TenantMapper tenantMapper;
  @Mock private GravitinoUserService gravitinoUserService;
  @Mock private GravitinoUserRoleService gravitinoUserRoleService;
  @Mock private GravitinoUserDataPrivilegeService gravitinoUserDataPrivilegeService;

  private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    TenantContextHolder.set(new TenantContext(10L, "tenant-10", null, null, false));
    MockHttpServletRequest request = new MockHttpServletRequest();
    TrustedIdentityContext.attach(
        request,
        new TrustedIdentityContext(
            900L,
            10L,
            "tenant-10",
            "tenant_admin",
            "admin@datapillar.com",
            List.of("ADMIN"),
            false,
            null,
            null,
            "token-1"));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    userService =
        new UserServiceImpl(
            userMapper,
            tenantUserMapper,
            passwordEncoder,
            permissionMapper,
            featureObjectMapper,
            roleMapper,
            tenantMapper,
            gravitinoUserService,
            gravitinoUserRoleService,
            gravitinoUserDataPrivilegeService);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void createUser_shouldProvisionGravitinoAndSyncRoles() {
    UserCreateRequest request = new UserCreateRequest();
    request.setUsername("sunny");
    request.setPassword("123456asd");
    request.setRoleIds(List.of(9L));

    Role role = new Role();
    role.setId(9L);
    role.setTenantId(10L);
    role.setName("Developer");

    when(userMapper.selectByUsernameGlobal("sunny")).thenReturn(null);
    when(passwordEncoder.encode("123456asd")).thenReturn("encoded-password");
    when(tenantUserMapper.countByUserId(700L)).thenReturn(0);
    when(roleMapper.selectById(9L)).thenReturn(role);
    when(roleMapper.findByUserId(10L, 700L)).thenReturn(List.of(role));
    when(gravitinoUserService.createUser("sunny", 700L, "tenant_admin"))
        .thenReturn(List.of("oneMeta", "oneSemantics"));
    when(userMapper.insert(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              user.setId(700L);
              return 1;
            });

    Long userId = userService.createUser(request);

    assertEquals(700L, userId);
    verify(gravitinoUserService).createUser("sunny", 700L, "tenant_admin");
    verify(gravitinoUserRoleService)
        .replaceUserRoles("sunny", List.of("Developer"), "tenant_admin");
  }

  @Test
  void deleteUser_shouldClearRemoteAccessBeforeDeletingRemoteUser() {
    User user = new User();
    user.setId(501L);
    user.setTenantId(10L);
    user.setUsername("member_user");
    user.setLevel(100);
    when(userMapper.selectByIdAndTenantId(10L, 501L)).thenReturn(user);

    userService.deleteUser(501L);

    verify(tenantUserMapper).deleteByTenantIdAndUserId(10L, 501L);
    verify(userMapper).deleteUserRoles(10L, 501L);
    var inOrder =
        inOrder(gravitinoUserRoleService, gravitinoUserDataPrivilegeService, gravitinoUserService);
    inOrder.verify(gravitinoUserRoleService).revokeAllUserRoles("member_user", "tenant_admin");
    inOrder
        .verify(gravitinoUserDataPrivilegeService)
        .clearUserDataPrivileges(
            501L, "member_user", GravitinoDomainRoutingService.DOMAIN_ALL, "tenant_admin");
    inOrder.verify(gravitinoUserService).deleteUser("member_user", "tenant_admin");
  }

  @Test
  void getUserDataPrivileges_shouldDelegateToGravitinoService() {
    User user = new User();
    user.setId(501L);
    user.setTenantId(10L);
    user.setUsername("member_user");
    when(userMapper.selectByIdAndTenantId(10L, 501L)).thenReturn(user);

    RoleDataPrivilegeItem item = new RoleDataPrivilegeItem();
    item.setObjectName("ods.orders");
    when(gravitinoUserDataPrivilegeService.getUserDataPrivileges(
            501L, "member_user", "metadata", null))
        .thenReturn(List.of(item));

    List<RoleDataPrivilegeItem> result = userService.getUserDataPrivileges(501L, "metadata");

    assertEquals(1, result.size());
    assertEquals("ods.orders", result.getFirst().getObjectName());
  }

  @Test
  void replaceUserDataPrivileges_shouldDelegateToGravitinoService() {
    User user = new User();
    user.setId(501L);
    user.setTenantId(10L);
    user.setUsername("member_user");
    when(userMapper.selectByIdAndTenantId(10L, 501L)).thenReturn(user);

    RoleDataPrivilegeCommandItem command = new RoleDataPrivilegeCommandItem();
    command.setObjectType("TABLE");
    command.setObjectName("ods.orders");
    command.setPrivilegeCodes(List.of("SELECT"));

    userService.replaceUserDataPrivileges(501L, "metadata", List.of(command));

    verify(gravitinoUserDataPrivilegeService)
        .replaceUserDataPrivileges(
            eq(501L), eq("member_user"), eq("metadata"), eq(List.of(command)), eq(null));
  }
}
