package com.sunny.datapillar.studio.module.setup.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
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
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSetupService;
import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.RolePermissionMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserRoleMapper;
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SetupServiceImplTest {

  @Mock private TenantMapper tenantMapper;
  @Mock private SystemBootstrapMapper systemBootstrapMapper;
  @Mock private TenantService tenantService;
  @Mock private UserMapper userMapper;
  @Mock private UserService userService;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private UserRoleMapper userRoleMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private RolePermissionMapper rolePermissionMapper;
  @Mock private PermissionMapper permissionMapper;
  @Mock private FeatureObjectMapper featureObjectMapper;
  @Mock private TenantFeaturePermissionMapper tenantFeaturePermissionMapper;
  @Mock private GravitinoSetupService gravitinoSetupService;
  @Mock private TransactionTemplate transactionTemplate;

  private SetupServiceImpl setupService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
    setupService =
        new SetupServiceImpl(
            tenantMapper,
            systemBootstrapMapper,
            tenantService,
            userMapper,
            userService,
            tenantUserMapper,
            userRoleMapper,
            roleMapper,
            rolePermissionMapper,
            permissionMapper,
            featureObjectMapper,
            tenantFeaturePermissionMapper,
            gravitinoSetupService,
            transactionTemplate);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void getStatus_shouldReturnSchemaNotReadyWhenBootstrapMissing() {
    when(systemBootstrapMapper.selectById(1)).thenReturn(null);

    SetupStatusResponse response = setupService.getStatus();

    assertFalse(response.isSchemaReady());
    assertFalse(response.isInitialized());
    assertEquals("SCHEMA_MIGRATION", response.getCurrentStep());
    assertNotNull(response.getSteps());
    assertEquals(3, response.getSteps().size());
    assertEquals("SCHEMA_MIGRATION", response.getSteps().get(0).getCode());
    assertEquals("IN_PROGRESS", response.getSteps().get(0).getStatus());
  }

  @Test
  void getStatus_shouldReturnInitializationStepWhenSchemaReadyButNotInitialized() {
    when(systemBootstrapMapper.selectById(1)).thenReturn(bootstrap(0));

    SetupStatusResponse response = setupService.getStatus();

    assertTrue(response.isSchemaReady());
    assertFalse(response.isInitialized());
    assertEquals("SYSTEM_INITIALIZATION", response.getCurrentStep());
    assertNotNull(response.getSteps());
    assertEquals("COMPLETED", response.getSteps().get(0).getStatus());
    assertEquals("IN_PROGRESS", response.getSteps().get(1).getStatus());
    assertEquals("PENDING", response.getSteps().get(2).getStatus());
  }

  @Test
  void getStatus_shouldReturnCompletedStepWhenAlreadyInitialized() {
    when(systemBootstrapMapper.selectById(1)).thenReturn(bootstrap(3));

    SetupStatusResponse response = setupService.getStatus();

    assertTrue(response.isSchemaReady());
    assertTrue(response.isInitialized());
    assertEquals("COMPLETED", response.getCurrentStep());
    assertNotNull(response.getSteps());
    assertEquals("COMPLETED", response.getSteps().get(0).getStatus());
    assertEquals("COMPLETED", response.getSteps().get(1).getStatus());
    assertEquals("COMPLETED", response.getSteps().get(2).getStatus());
  }

  @Test
  void getStatus_shouldReturnFailedInitializationStepWhenProvisioningFailed() {
    when(systemBootstrapMapper.selectById(1)).thenReturn(bootstrap(2));

    SetupStatusResponse response = setupService.getStatus();

    assertTrue(response.isSchemaReady());
    assertFalse(response.isInitialized());
    assertEquals("SYSTEM_INITIALIZATION", response.getCurrentStep());
    assertEquals("COMPLETED", response.getSteps().get(0).getStatus());
    assertEquals("FAILED", response.getSteps().get(1).getStatus());
    assertEquals("PENDING", response.getSteps().get(2).getStatus());
  }

  @Test
  void initialize_shouldRejectWhenSchemaNotReady() {
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(null);

    ServiceUnavailableException exception =
        assertThrows(
            ServiceUnavailableException.class,
            () -> setupService.initialize(buildInitializeRequest()));

    assertEquals("Service unavailable", exception.getMessage());
  }

  @Test
  void initialize_shouldRejectWhenSystemAlreadyInitialized() {
    SystemBootstrap bootstrap = bootstrap(3);
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap);

    AlreadyExistsException exception =
        assertThrows(
            AlreadyExistsException.class, () -> setupService.initialize(buildInitializeRequest()));

    assertEquals("Resource already exists", exception.getMessage());
  }

  @Test
  void initialize_shouldCreateAdminTenantAndGrantPermissions() {
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap(0));
    when(tenantMapper.selectByCode(any())).thenReturn(null);
    when(tenantService.createTenant(any(), eq(false))).thenReturn(100L);
    when(userMapper.selectByUsernameGlobal(any())).thenReturn(null);
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userService.createUser(any(), eq(false))).thenReturn(200L);
    when(userMapper.selectById(200L)).thenReturn(adminUser(200L, "sunny"));
    when(userMapper.updateById(any(User.class))).thenReturn(1);
    when(tenantUserMapper.selectByTenantIdAndUserId(100L, 200L))
        .thenAnswer(
            invocation -> {
              assertEquals(100L, TenantContextHolder.getTenantId());
              assertEquals(200L, TenantContextHolder.getActorUserId());
              assertEquals(100L, TenantContextHolder.getActorTenantId());
              return null;
            });
    when(roleMapper.findByName(anyLong(), any())).thenReturn(null);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(300L);
              return 1;
            });
    when(userRoleMapper.selectCount(any())).thenReturn(0L);

    Permission adminPermission = new Permission();
    adminPermission.setId(3L);
    when(permissionMapper.selectSystemByCode("ADMIN")).thenReturn(adminPermission);

    FeatureObject activeObject = new FeatureObject();
    activeObject.setId(1L);
    activeObject.setStatus(1);
    when(featureObjectMapper.selectList(any())).thenReturn(List.of(activeObject));
    when(tenantFeaturePermissionMapper.selectByTenantIdAndObjectId(anyLong(), anyLong()))
        .thenReturn(null);
    when(systemBootstrapMapper.updateById(any(SystemBootstrap.class))).thenReturn(1);

    SetupInitializeResponse response = setupService.initialize(buildInitializeRequest());

    assertEquals(100L, response.getTenantId());
    assertEquals(200L, response.getUserId());

    ArgumentCaptor<TenantCreateRequest> tenantCaptor =
        ArgumentCaptor.forClass(TenantCreateRequest.class);
    verify(tenantService).createTenant(tenantCaptor.capture(), eq(false));
    assertEquals("tenant-acme-data", tenantCaptor.getValue().getCode());
    assertEquals("ACME Data", tenantCaptor.getValue().getName());

    ArgumentCaptor<SystemBootstrap> bootstrapCaptor =
        ArgumentCaptor.forClass(SystemBootstrap.class);
    verify(systemBootstrapMapper, org.mockito.Mockito.times(4))
        .updateById(bootstrapCaptor.capture());
    List<SystemBootstrap> bootstrapUpdates = bootstrapCaptor.getAllValues();
    SystemBootstrap finalBootstrap = bootstrapUpdates.get(bootstrapUpdates.size() - 1);
    assertEquals(3, finalBootstrap.getStatus());
    assertEquals(100L, finalBootstrap.getSetupTenantId());
    assertEquals(200L, finalBootstrap.getSetupAdminUserId());
    assertNull(finalBootstrap.getSetupTokenHash());
    assertNull(finalBootstrap.getSetupTokenGeneratedAt());
    assertTrue(finalBootstrap.getSetupCompletedAt() != null);
    assertNull(TenantContextHolder.get());

    verify(gravitinoSetupService)
        .initializeResources(100L, "tenant-acme-data", 200L, "sunny", "Platform over management");
  }

  @Test
  void initialize_shouldMarkSetupFailedWhenGravitinoSetupFails() {
    SystemBootstrap bootstrap = bootstrap(0);
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap);
    when(tenantMapper.selectByCode(any())).thenReturn(null);
    when(tenantService.createTenant(any(), eq(false))).thenReturn(100L);
    when(userMapper.selectByUsernameGlobal(any())).thenReturn(null);
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userService.createUser(any(), eq(false))).thenReturn(200L);
    when(userMapper.selectById(200L)).thenReturn(adminUser(200L, "sunny"));
    when(userMapper.updateById(any(User.class))).thenReturn(1);
    when(tenantUserMapper.selectByTenantIdAndUserId(100L, 200L)).thenReturn(null);
    when(roleMapper.findByName(anyLong(), any())).thenReturn(null);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(300L);
              return 1;
            });
    when(userRoleMapper.selectCount(any())).thenReturn(0L);

    Permission adminPermission = new Permission();
    adminPermission.setId(3L);
    when(permissionMapper.selectSystemByCode("ADMIN")).thenReturn(adminPermission);

    FeatureObject activeObject = new FeatureObject();
    activeObject.setId(1L);
    activeObject.setStatus(1);
    when(featureObjectMapper.selectList(any())).thenReturn(List.of(activeObject));
    when(tenantFeaturePermissionMapper.selectByTenantIdAndObjectId(anyLong(), anyLong()))
        .thenReturn(null);
    when(systemBootstrapMapper.updateById(any(SystemBootstrap.class))).thenReturn(1);
    doThrow(new RuntimeException("remote failed"))
        .when(gravitinoSetupService)
        .initializeResources(100L, "tenant-acme-data", 200L, "sunny", "Platform over management");

    assertThrows(RuntimeException.class, () -> setupService.initialize(buildInitializeRequest()));

    verify(userMapper, never()).deleteById(200L);
    verify(tenantMapper, never()).deleteById(100L);
    verify(roleMapper, never()).delete(any());

    ArgumentCaptor<SystemBootstrap> bootstrapCaptor =
        ArgumentCaptor.forClass(SystemBootstrap.class);
    verify(systemBootstrapMapper, org.mockito.Mockito.times(4))
        .updateById(bootstrapCaptor.capture());
    List<SystemBootstrap> bootstrapUpdates = bootstrapCaptor.getAllValues();
    SystemBootstrap finalBootstrap = bootstrapUpdates.get(bootstrapUpdates.size() - 1);
    assertEquals(2, finalBootstrap.getStatus());
    assertEquals(100L, finalBootstrap.getSetupTenantId());
    assertEquals(200L, finalBootstrap.getSetupAdminUserId());
  }

  @Test
  void initialize_shouldRecoverWhenBootstrapTenantMissing() {
    SystemBootstrap bootstrap = bootstrap(0);
    bootstrap.setSetupTenantId(999L);
    bootstrap.setSetupAdminUserId(200L);
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap);
    when(tenantMapper.selectByCode(any())).thenReturn(null);
    when(tenantMapper.selectById(999L)).thenReturn(null);
    when(tenantService.createTenant(any(), eq(false))).thenReturn(100L);
    when(userMapper.selectById(200L)).thenReturn(adminUser(200L, "sunny"));
    when(tenantUserMapper.selectByTenantIdAndUserId(100L, 200L)).thenReturn(null);
    when(roleMapper.findByName(anyLong(), any())).thenReturn(null);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(300L);
              return 1;
            });
    when(userRoleMapper.selectCount(any())).thenReturn(0L);

    Permission adminPermission = new Permission();
    adminPermission.setId(3L);
    when(permissionMapper.selectSystemByCode("ADMIN")).thenReturn(adminPermission);

    FeatureObject activeObject = new FeatureObject();
    activeObject.setId(1L);
    activeObject.setStatus(1);
    when(featureObjectMapper.selectList(any())).thenReturn(List.of(activeObject));
    when(tenantFeaturePermissionMapper.selectByTenantIdAndObjectId(anyLong(), anyLong()))
        .thenReturn(null);
    when(systemBootstrapMapper.updateById(any(SystemBootstrap.class))).thenReturn(1);

    SetupInitializeResponse response = setupService.initialize(buildInitializeRequest());

    assertEquals(100L, response.getTenantId());
    assertEquals(200L, response.getUserId());
    verify(tenantService).createTenant(any(), eq(false));
    verify(userService, never()).createUser(any(), eq(false));

    ArgumentCaptor<SystemBootstrap> bootstrapCaptor =
        ArgumentCaptor.forClass(SystemBootstrap.class);
    verify(systemBootstrapMapper, org.mockito.Mockito.times(4))
        .updateById(bootstrapCaptor.capture());
    List<SystemBootstrap> bootstrapUpdates = bootstrapCaptor.getAllValues();
    SystemBootstrap finalBootstrap = bootstrapUpdates.get(bootstrapUpdates.size() - 1);
    assertEquals(100L, finalBootstrap.getSetupTenantId());
    assertEquals(200L, finalBootstrap.getSetupAdminUserId());
  }

  @Test
  void initialize_shouldRecoverWhenBootstrapAdminUserMissing() {
    SystemBootstrap bootstrap = bootstrap(0);
    bootstrap.setSetupTenantId(100L);
    bootstrap.setSetupAdminUserId(999L);
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap);
    when(tenantMapper.selectById(100L)).thenReturn(tenant(100L, "tenant-acme-data"));
    when(userMapper.selectById(999L)).thenReturn(null);
    when(userMapper.selectByUsernameGlobal(any())).thenReturn(null);
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userService.createUser(any(), eq(false))).thenReturn(200L);
    when(userMapper.selectById(200L)).thenReturn(adminUser(200L, "sunny"));
    when(userMapper.updateById(any(User.class))).thenReturn(1);
    when(tenantUserMapper.selectByTenantIdAndUserId(100L, 200L)).thenReturn(null);
    when(roleMapper.findByName(anyLong(), any())).thenReturn(null);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(300L);
              return 1;
            });
    when(userRoleMapper.selectCount(any())).thenReturn(0L);

    Permission adminPermission = new Permission();
    adminPermission.setId(3L);
    when(permissionMapper.selectSystemByCode("ADMIN")).thenReturn(adminPermission);

    FeatureObject activeObject = new FeatureObject();
    activeObject.setId(1L);
    activeObject.setStatus(1);
    when(featureObjectMapper.selectList(any())).thenReturn(List.of(activeObject));
    when(tenantFeaturePermissionMapper.selectByTenantIdAndObjectId(anyLong(), anyLong()))
        .thenReturn(null);
    when(systemBootstrapMapper.updateById(any(SystemBootstrap.class))).thenReturn(1);

    SetupInitializeResponse response = setupService.initialize(buildInitializeRequest());

    assertEquals(100L, response.getTenantId());
    assertEquals(200L, response.getUserId());
    verify(tenantService, never()).createTenant(any(), eq(false));
    verify(userService).createUser(any(), eq(false));

    ArgumentCaptor<SystemBootstrap> bootstrapCaptor =
        ArgumentCaptor.forClass(SystemBootstrap.class);
    verify(systemBootstrapMapper, org.mockito.Mockito.times(4))
        .updateById(bootstrapCaptor.capture());
    List<SystemBootstrap> bootstrapUpdates = bootstrapCaptor.getAllValues();
    SystemBootstrap finalBootstrap = bootstrapUpdates.get(bootstrapUpdates.size() - 1);
    assertEquals(100L, finalBootstrap.getSetupTenantId());
    assertEquals(200L, finalBootstrap.getSetupAdminUserId());
  }

  @Test
  void initialize_shouldRejectWhenUsernameAlreadyExists() {
    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap(0));

    User existingUser = new User();
    existingUser.setId(999L);
    when(userMapper.selectByUsernameGlobal("sunny")).thenReturn(existingUser);

    AlreadyExistsException exception =
        assertThrows(
            AlreadyExistsException.class, () -> setupService.initialize(buildInitializeRequest()));

    assertEquals("Resource already exists", exception.getMessage());
    verify(tenantService, never()).createTenant(any(), eq(false));
  }

  @Test
  void initialize_shouldRestorePreviousTenantContextAfterExecution() {
    TenantContext previousContext = new TenantContext(88L, "tenant-prev", 99L, 88L, false);
    TenantContextHolder.set(previousContext);

    when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap(0));
    when(tenantMapper.selectByCode(any())).thenReturn(null);
    when(tenantService.createTenant(any(), eq(false))).thenReturn(100L);
    when(userMapper.selectByUsernameGlobal(any())).thenReturn(null);
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userService.createUser(any(), eq(false))).thenReturn(200L);
    when(userMapper.selectById(200L)).thenReturn(adminUser(200L, "sunny"));
    when(userMapper.updateById(any(User.class))).thenReturn(1);
    when(roleMapper.findByName(anyLong(), any())).thenReturn(null);
    when(roleMapper.insert(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setId(300L);
              return 1;
            });
    when(userRoleMapper.selectCount(any())).thenReturn(0L);
    Permission adminPermission = new Permission();
    adminPermission.setId(3L);
    when(permissionMapper.selectSystemByCode("ADMIN")).thenReturn(adminPermission);
    when(featureObjectMapper.selectList(any())).thenReturn(List.of());
    when(systemBootstrapMapper.updateById(any(SystemBootstrap.class))).thenReturn(1);

    setupService.initialize(buildInitializeRequest());

    assertSame(previousContext, TenantContextHolder.get());
  }

  private SetupInitializeRequest buildInitializeRequest() {
    SetupInitializeRequest request = new SetupInitializeRequest();
    request.setOrganizationName("ACME Data");
    request.setAdminName("Sunny");
    request.setUsername("sunny");
    request.setPassword("123456asd");
    request.setEmail("Sunny@DataPillar.com");
    return request;
  }

  private SystemBootstrap bootstrap(int setupCompleted) {
    SystemBootstrap bootstrap = new SystemBootstrap();
    bootstrap.setId(1);
    bootstrap.setStatus(setupCompleted);
    return bootstrap;
  }

  private com.sunny.datapillar.studio.module.tenant.entity.Tenant tenant(Long id, String code) {
    com.sunny.datapillar.studio.module.tenant.entity.Tenant tenant =
        new com.sunny.datapillar.studio.module.tenant.entity.Tenant();
    tenant.setId(id);
    tenant.setCode(code);
    return tenant;
  }

  private User adminUser(Long id, String username) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }
}
