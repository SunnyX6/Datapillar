package com.sunny.datapillar.studio.module.setup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.gravitino.GravitinoConnector;
import com.sunny.datapillar.connector.runtime.ConnectorKernel;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.IdempotencyDescriptor;
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
import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import com.sunny.datapillar.studio.module.setup.service.SetupService;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeaturePermission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.RolePermissionMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Initialize service implementation Implement initialization business process and rule verification
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class SetupServiceImpl implements SetupService {

  private static final String DEFAULT_TENANT_TYPE = "ENTERPRISE";
  private static final String ADMIN_ROLE_TYPE = "ADMIN";
  private static final String ADMIN_ROLE_NAME = "Platform over management";
  private static final int ADMIN_ROLE_SORT = 0;
  private static final String ADMIN_PERMISSION_CODE = "ADMIN";
  private static final String GRANT_SOURCE_SYSTEM = "SYSTEM";
  private static final int STATUS_ENABLED = 1;
  private static final int PLATFORM_SUPER_ADMIN_LEVEL = 0;
  private static final int USER_NOT_DELETED = 0;
  private static final int MAX_TENANT_CODE_LENGTH = 64;
  private static final String DEFAULT_TENANT_CODE_PREFIX = "tenant";
  private static final int SYSTEM_BOOTSTRAP_ID = 1;
  private static final int SETUP_COMPLETED = 1;
  private static final String STEP_SCHEMA_MIGRATION = "SCHEMA_MIGRATION";
  private static final String STEP_SYSTEM_INITIALIZATION = "SYSTEM_INITIALIZATION";
  private static final String STEP_COMPLETED = "COMPLETED";
  private static final String STEP_STATUS_PENDING = "PENDING";
  private static final String STEP_STATUS_IN_PROGRESS = "IN_PROGRESS";
  private static final String STEP_STATUS_COMPLETED = "COMPLETED";
  private static final String METALAKE_ONE_META = "oneMeta";
  private static final String METALAKE_ONE_SEMANTICS = "oneSemantics";
  private static final String SETUP_STEP_SYNC_USER = "SETUP_SYNC_USER";
  private static final String SETUP_STEP_CREATE_METALAKE_METADATA =
      "SETUP_CREATE_METALAKE_METADATA";
  private static final String SETUP_STEP_CREATE_METALAKE_SEMANTIC =
      "SETUP_CREATE_METALAKE_SEMANTIC";
  private static final String SETUP_STEP_SYNC_ROLE_DATA_PRIVILEGES =
      "SETUP_SYNC_ROLE_DATA_PRIVILEGES";
  private static final Argon2PasswordEncoder ARGON2_PASSWORD_ENCODER =
      new Argon2PasswordEncoder(16, 32, 1, 64 * 1024, 3);
  private final TenantMapper tenantMapper;
  private final SystemBootstrapMapper systemBootstrapMapper;
  private final TenantService tenantService;
  private final UserMapper userMapper;
  private final TenantUserMapper tenantUserMapper;
  private final UserRoleMapper userRoleMapper;
  private final RoleMapper roleMapper;
  private final RolePermissionMapper rolePermissionMapper;
  private final PermissionMapper permissionMapper;
  private final FeatureObjectMapper featureObjectMapper;
  private final TenantFeaturePermissionMapper tenantFeaturePermissionMapper;
  private final ConnectorKernel connectorKernel;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  @Override
  @Transactional(readOnly = true)
  public SetupStatusResponse getStatus() {
    SetupStatusResponse response = new SetupStatusResponse();
    SystemBootstrap bootstrap = systemBootstrapMapper.selectById(SYSTEM_BOOTSTRAP_ID);
    boolean schemaReady = bootstrap != null;
    boolean initialized = isSetupCompleted(bootstrap);
    response.setSchemaReady(schemaReady);
    response.setInitialized(initialized);
    if (!schemaReady) {
      response.setCurrentStep(STEP_SCHEMA_MIGRATION);
    } else if (!initialized) {
      response.setCurrentStep(STEP_SYSTEM_INITIALIZATION);
    } else {
      response.setCurrentStep(STEP_COMPLETED);
    }

    response.setSteps(buildStepStatuses(schemaReady, initialized));
    return response;
  }

  @Override
  public SetupInitializeResponse initialize(SetupInitializeRequest request) {
    if (request == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Parameter error");
    }

    SetupProvisionContext context = prepareSetupProvisioning(request);
    synchronizeSetupResources(context);
    completeSetupProvisioning(context);

    SetupInitializeResponse response = new SetupInitializeResponse();
    response.setTenantId(context.tenantId());
    response.setUserId(context.adminUserId());
    return response;
  }

  private boolean isSetupCompleted(SystemBootstrap bootstrap) {
    return bootstrap != null
        && bootstrap.getSetupCompleted() != null
        && bootstrap.getSetupCompleted() == SETUP_COMPLETED;
  }

  private List<SetupStepStatusItem> buildStepStatuses(boolean schemaReady, boolean initialized) {
    return List.of(
        createStepStatus(
            STEP_SCHEMA_MIGRATION,
            "Database migration",
            "execute Flyway Structure and Dictionary Migration",
            resolveSchemaMigrationStatus(schemaReady)),
        createStepStatus(
            STEP_SYSTEM_INITIALIZATION,
            "Administrator initialization",
            "Create first tenant,Administrator and permission binding",
            resolveInitStatus(schemaReady, initialized)),
        createStepStatus(
            STEP_COMPLETED,
            "Initialization completed",
            "The system enters the login state",
            initialized ? STEP_STATUS_COMPLETED : STEP_STATUS_PENDING));
  }

  private String resolveSchemaMigrationStatus(boolean schemaReady) {
    return schemaReady ? STEP_STATUS_COMPLETED : STEP_STATUS_IN_PROGRESS;
  }

  private String resolveInitStatus(boolean schemaReady, boolean initialized) {
    if (!schemaReady) {
      return STEP_STATUS_PENDING;
    }
    if (initialized) {
      return STEP_STATUS_COMPLETED;
    }
    return STEP_STATUS_IN_PROGRESS;
  }

  private SetupStepStatusItem createStepStatus(
      String code, String name, String description, String status) {
    SetupStepStatusItem stepStatus = new SetupStepStatusItem();
    stepStatus.setCode(code);
    stepStatus.setName(name);
    stepStatus.setDescription(description);
    stepStatus.setStatus(status);
    return stepStatus;
  }

  private SetupProvisionContext prepareSetupProvisioning(SetupInitializeRequest request) {
    return runInTransaction(
        () -> {
          SystemBootstrap bootstrap =
              systemBootstrapMapper.selectByIdForUpdate(SYSTEM_BOOTSTRAP_ID);
          if (bootstrap == null) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                "Service unavailable", "Database migration not completed");
          }
          if (isSetupCompleted(bootstrap)) {
            throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
                "Resource already exists", "System has been initialized");
          }

          Long tenantId = bootstrap.getSetupTenantId();
          if (tenantId == null || tenantId <= 0) {
            tenantId = createTenant(request);
            bootstrap.setSetupTenantId(tenantId);
          }

          String tenantCode = requireTenantCode(tenantId);
          Long adminUserId = bootstrap.getSetupAdminUserId();
          User adminUser;
          if (adminUserId == null || adminUserId <= 0) {
            adminUser = createAdminUser(tenantId, request);
            bootstrap.setSetupAdminUserId(adminUser.getId());
          } else {
            adminUser = userMapper.selectById(adminUserId);
            if (adminUser == null) {
              throw new com.sunny.datapillar.common.exception.InternalException(
                  "Server internal error");
            }
          }

          TenantContext previousContext = TenantContextHolder.get();
          TenantContext setupContext =
              new TenantContext(tenantId, tenantCode, adminUser.getId(), tenantId, false);
          TenantContextHolder.set(setupContext);
          Role adminRole;
          try {
            bindTenantUser(tenantId, adminUser.getId());
            adminRole = upsertAdminRole(tenantId);
            bindUserRole(tenantId, adminUser.getId(), adminRole.getId());
            grantTenantAndRolePermissions(tenantId, adminUser.getId(), adminRole.getId());
          } finally {
            restoreTenantContext(previousContext);
          }

          bootstrap.setSetupCompleted(0);
          bootstrap.setSetupTokenHash(null);
          bootstrap.setSetupTokenGeneratedAt(null);
          int updated = systemBootstrapMapper.updateById(bootstrap);
          if (updated == 0) {
            throw new com.sunny.datapillar.common.exception.InternalException(
                "Server internal error");
          }

          return new SetupProvisionContext(
              tenantId,
              tenantCode,
              adminUser.getId(),
              adminUser.getUsername(),
              adminRole.getName());
        });
  }

  private void synchronizeSetupResources(SetupProvisionContext context) {
    ConnectorContext connectorContext =
        new ConnectorContext(
            context.tenantId(),
            context.tenantCode(),
            context.adminUserId(),
            context.adminUsername(),
            null,
            context.adminUserId(),
            context.tenantId(),
            false,
            null,
            "setup:" + context.tenantId() + ":" + context.adminUserId());

    invokeGravitinoWrite(
        GravitinoConnector.OP_SECURITY_SYNC_USER,
        objectMapper.createObjectNode().put("username", context.adminUsername()),
        context,
        SETUP_STEP_SYNC_USER,
        connectorContext);

    invokeGravitinoWrite(
        GravitinoConnector.OP_METALAKE_CREATE,
        objectMapper
            .createObjectNode()
            .put("metalakeName", METALAKE_ONE_META)
            .put("comment", "Datapillar metadata metalake"),
        context,
        SETUP_STEP_CREATE_METALAKE_METADATA,
        connectorContext);

    invokeGravitinoWrite(
        GravitinoConnector.OP_METALAKE_CREATE,
        objectMapper
            .createObjectNode()
            .put("metalakeName", METALAKE_ONE_SEMANTICS)
            .put("comment", "Datapillar semantic metalake"),
        context,
        SETUP_STEP_CREATE_METALAKE_SEMANTIC,
        connectorContext);

    invokeGravitinoWrite(
        GravitinoConnector.OP_SECURITY_SYNC_ROLE_DATA_PRIVILEGES,
        objectMapper
            .createObjectNode()
            .put("roleName", context.adminRoleName())
            .put("domain", "METADATA")
            .set("commands", objectMapper.createArrayNode()),
        context,
        SETUP_STEP_SYNC_ROLE_DATA_PRIVILEGES,
        connectorContext);
  }

  private void invokeGravitinoWrite(
      String operationId,
      com.fasterxml.jackson.databind.node.ObjectNode payload,
      SetupProvisionContext context,
      String step,
      ConnectorContext connectorContext) {
    String key = "setup:%d:%d:%s".formatted(context.tenantId(), context.adminUserId(), step);
    ConnectorInvocation invocation =
        ConnectorInvocation.builder(GravitinoConnector.CONNECTOR_ID, operationId)
            .payload(payload)
            .context(connectorContext)
            .idempotency(IdempotencyDescriptor.of(key, step))
            .build();
    connectorKernel.invoke(invocation);
  }

  private void completeSetupProvisioning(SetupProvisionContext context) {
    runInTransactionWithoutResult(
        () -> {
          SystemBootstrap bootstrap =
              systemBootstrapMapper.selectByIdForUpdate(SYSTEM_BOOTSTRAP_ID);
          if (bootstrap == null) {
            throw new com.sunny.datapillar.common.exception.InternalException(
                "Server internal error");
          }
          bootstrap.setSetupCompleted(SETUP_COMPLETED);
          bootstrap.setSetupTenantId(context.tenantId());
          bootstrap.setSetupAdminUserId(context.adminUserId());
          bootstrap.setSetupTokenHash(null);
          bootstrap.setSetupTokenGeneratedAt(null);
          bootstrap.setSetupCompletedAt(LocalDateTime.now());
          int updated = systemBootstrapMapper.updateById(bootstrap);
          if (updated == 0) {
            throw new com.sunny.datapillar.common.exception.InternalException(
                "Server internal error");
          }
        });
  }

  private Long createTenant(SetupInitializeRequest request) {
    TenantCreateRequest tenant = new TenantCreateRequest();
    tenant.setCode(resolveTenantCode(request));
    tenant.setName(resolveTenantName(request));
    tenant.setType(DEFAULT_TENANT_TYPE);
    return tenantService.createTenant(tenant);
  }

  private String requireTenantCode(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }
    com.sunny.datapillar.studio.module.tenant.entity.Tenant tenant =
        tenantMapper.selectById(tenantId);
    if (tenant == null || !StringUtils.hasText(tenant.getCode())) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }
    return tenant.getCode().trim();
  }

  private User createAdminUser(Long tenantId, SetupInitializeRequest request) {
    String email = resolveAdminEmail(request);
    String username = resolveAdminUsername(request);
    LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
    emailQuery.eq(User::getEmail, email).eq(User::getDeleted, USER_NOT_DELETED);
    if (userMapper.selectOne(emailQuery) != null) {
      throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
          "Resource already exists", email);
    }

    User user = new User();
    user.setTenantId(tenantId);
    user.setUsername(username);
    user.setPassword(ARGON2_PASSWORD_ENCODER.encode(resolveAdminPassword(request)));
    user.setNickname(resolveAdminDisplayName(request));
    user.setEmail(email);
    user.setLevel(PLATFORM_SUPER_ADMIN_LEVEL);
    user.setStatus(STATUS_ENABLED);
    user.setDeleted(USER_NOT_DELETED);
    int inserted = userMapper.insert(user);
    if (inserted == 0 || user.getId() == null) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }
    return user;
  }

  private void bindTenantUser(Long tenantId, Long userId) {
    TenantUser existing = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
    if (existing != null) {
      LambdaUpdateWrapper<TenantUser> updateWrapper = new LambdaUpdateWrapper<>();
      updateWrapper
          .eq(TenantUser::getTenantId, tenantId)
          .eq(TenantUser::getUserId, userId)
          .set(TenantUser::getStatus, STATUS_ENABLED)
          .set(TenantUser::getIsDefault, 1);
      tenantUserMapper.update(null, updateWrapper);
      return;
    }

    TenantUser tenantUser = new TenantUser();
    tenantUser.setTenantId(tenantId);
    tenantUser.setUserId(userId);
    tenantUser.setStatus(STATUS_ENABLED);
    tenantUser.setIsDefault(1);
    tenantUserMapper.insert(tenantUser);
  }

  private Role upsertAdminRole(Long tenantId) {
    Role existing = roleMapper.findByName(tenantId, ADMIN_ROLE_NAME);
    if (existing != null) {
      existing.setType(ADMIN_ROLE_TYPE);
      existing.setStatus(STATUS_ENABLED);
      existing.setSort(ADMIN_ROLE_SORT);
      existing.setLevel(PLATFORM_SUPER_ADMIN_LEVEL);
      roleMapper.updateById(existing);
      return existing;
    }

    Role role = new Role();
    role.setTenantId(tenantId);
    role.setType(ADMIN_ROLE_TYPE);
    role.setName(ADMIN_ROLE_NAME);
    role.setDescription("The highest authority role on the platform");
    role.setLevel(PLATFORM_SUPER_ADMIN_LEVEL);
    role.setStatus(STATUS_ENABLED);
    role.setSort(ADMIN_ROLE_SORT);
    int inserted = roleMapper.insert(role);
    if (inserted == 0 || role.getId() == null) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }
    return role;
  }

  private void bindUserRole(Long tenantId, Long userId, Long roleId) {
    LambdaQueryWrapper<UserRole> query = new LambdaQueryWrapper<>();
    query
        .eq(UserRole::getTenantId, tenantId)
        .eq(UserRole::getUserId, userId)
        .eq(UserRole::getRoleId, roleId);
    Long count = userRoleMapper.selectCount(query);
    if (count != null && count > 0) {
      return;
    }

    UserRole userRole = new UserRole();
    userRole.setTenantId(tenantId);
    userRole.setUserId(userId);
    userRole.setRoleId(roleId);
    userRoleMapper.insert(userRole);
  }

  private void grantTenantAndRolePermissions(Long tenantId, Long operatorUserId, Long roleId) {
    Permission adminPermission = permissionMapper.selectSystemByCode(ADMIN_PERMISSION_CODE);
    if (adminPermission == null || adminPermission.getId() == null) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }

    LambdaQueryWrapper<FeatureObject> activeObjectQuery = new LambdaQueryWrapper<>();
    activeObjectQuery
        .eq(FeatureObject::getStatus, STATUS_ENABLED)
        .orderByAsc(FeatureObject::getSort)
        .orderByAsc(FeatureObject::getId);
    List<FeatureObject> featureObjects = featureObjectMapper.selectList(activeObjectQuery);
    if (featureObjects == null || featureObjects.isEmpty()) {
      return;
    }

    for (FeatureObject object : featureObjects) {
      if (object.getId() == null) {
        continue;
      }
      upsertTenantFeaturePermission(
          tenantId, object.getId(), adminPermission.getId(), operatorUserId);
      upsertRolePermission(tenantId, roleId, object.getId(), adminPermission.getId());
    }
  }

  private void upsertTenantFeaturePermission(
      Long tenantId, Long objectId, Long permissionId, Long operatorUserId) {
    TenantFeaturePermission existing =
        tenantFeaturePermissionMapper.selectByTenantIdAndObjectId(tenantId, objectId);
    if (existing == null) {
      TenantFeaturePermission permission = new TenantFeaturePermission();
      permission.setTenantId(tenantId);
      permission.setObjectId(objectId);
      permission.setPermissionId(permissionId);
      permission.setStatus(STATUS_ENABLED);
      permission.setGrantSource(GRANT_SOURCE_SYSTEM);
      permission.setGrantedBy(operatorUserId);
      permission.setUpdatedBy(operatorUserId);
      tenantFeaturePermissionMapper.insert(permission);
      return;
    }

    existing.setPermissionId(permissionId);
    existing.setStatus(STATUS_ENABLED);
    existing.setGrantSource(GRANT_SOURCE_SYSTEM);
    existing.setGrantedBy(operatorUserId);
    existing.setUpdatedBy(operatorUserId);
    tenantFeaturePermissionMapper.updateById(existing);
  }

  private void upsertRolePermission(Long tenantId, Long roleId, Long objectId, Long permissionId) {
    LambdaQueryWrapper<RolePermission> deleteWrapper = new LambdaQueryWrapper<>();
    deleteWrapper
        .eq(RolePermission::getTenantId, tenantId)
        .eq(RolePermission::getRoleId, roleId)
        .eq(RolePermission::getObjectId, objectId);
    rolePermissionMapper.delete(deleteWrapper);
    RolePermission rolePermission = new RolePermission();
    rolePermission.setTenantId(tenantId);
    rolePermission.setRoleId(roleId);
    rolePermission.setObjectId(objectId);
    rolePermission.setPermissionId(permissionId);
    rolePermissionMapper.insert(rolePermission);
  }

  private String resolveTenantCode(SetupInitializeRequest request) {
    String source =
        normalizeRequiredText(
            request.getOrganizationName(), "enterprise/Organization name cannot be empty");
    String normalized = normalizeCode(source);
    if (!StringUtils.hasText(normalized)) {
      normalized = Integer.toUnsignedString(source.hashCode(), 36);
    }
    return ensureUniqueTenantCode(applyTenantCodePrefix(normalized));
  }

  private String resolveTenantName(SetupInitializeRequest request) {
    return normalizeRequiredText(
        request.getOrganizationName(), "enterprise/Organization name cannot be empty");
  }

  private String resolveAdminDisplayName(SetupInitializeRequest request) {
    return normalizeRequiredText(request.getAdminName(), "Administrator name cannot be empty");
  }

  private String resolveAdminEmail(SetupInitializeRequest request) {
    return normalizeEmail(request.getEmail());
  }

  private String resolveAdminPassword(SetupInitializeRequest request) {
    return normalizeRequiredText(request.getPassword(), "Administrator password cannot be empty");
  }

  private String resolveAdminUsername(SetupInitializeRequest request) {
    String username =
        normalizeRequiredText(request.getUsername(), "Administrator username cannot be empty");
    if (userMapper.selectByUsernameGlobal(username) != null) {
      throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
          "Resource already exists", username);
    }
    return username;
  }

  private String applyTenantCodePrefix(String normalizedCode) {
    String code = normalizeCode(normalizedCode);
    if (!StringUtils.hasText(code)) {
      return DEFAULT_TENANT_CODE_PREFIX;
    }
    if (code.equals(DEFAULT_TENANT_CODE_PREFIX)
        || code.startsWith(DEFAULT_TENANT_CODE_PREFIX + "-")) {
      return code;
    }

    String prefixed = DEFAULT_TENANT_CODE_PREFIX + "-" + code;
    if (prefixed.length() <= MAX_TENANT_CODE_LENGTH) {
      return prefixed;
    }

    int tailMaxLength = MAX_TENANT_CODE_LENGTH - DEFAULT_TENANT_CODE_PREFIX.length() - 1;
    String tail = code.substring(0, Math.max(1, tailMaxLength));
    return (DEFAULT_TENANT_CODE_PREFIX + "-" + tail).replaceAll("[-_]+$", "");
  }

  private String ensureUniqueTenantCode(String baseCode) {
    String normalizedBase = normalizeCode(baseCode);
    if (!StringUtils.hasText(normalizedBase)) {
      normalizedBase = DEFAULT_TENANT_CODE_PREFIX;
    }

    if (tenantMapper.selectByCode(normalizedBase) == null) {
      return normalizedBase;
    }

    for (int index = 2; index <= 9999; index++) {
      String suffix = "-" + index;
      String candidate = appendSuffix(normalizedBase, suffix, MAX_TENANT_CODE_LENGTH);
      if (tenantMapper.selectByCode(candidate) == null) {
        return candidate;
      }
    }

    throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
        "Resource already exists", normalizedBase);
  }

  private String normalizeCode(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }

    String normalized =
        value
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("_+", "_")
            .replaceAll("^[-_]+", "")
            .replaceAll("[-_]+$", "");
    if (!StringUtils.hasText(normalized)) {
      return null;
    }

    if (normalized.length() > MAX_TENANT_CODE_LENGTH) {
      normalized =
          normalized
              .substring(0, MAX_TENANT_CODE_LENGTH)
              .replaceAll("^[-_]+", "")
              .replaceAll("[-_]+$", "");
    }
    return StringUtils.hasText(normalized) ? normalized : null;
  }

  private String appendSuffix(String base, String suffix, int maxLength) {
    if (!StringUtils.hasText(base)) {
      return suffix.substring(0, Math.min(maxLength, suffix.length()));
    }
    if (base.length() + suffix.length() <= maxLength) {
      return base + suffix;
    }
    int baseMaxLength = Math.max(1, maxLength - suffix.length());
    return base.substring(0, baseMaxLength) + suffix;
  }

  private String normalizeRequiredText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(message);
    }
    return value.trim();
  }

  private String normalizeEmail(String value) {
    String email = normalizeRequiredText(value, "Administrator email cannot be empty");
    return email.toLowerCase(Locale.ROOT);
  }

  private <T> T runInTransaction(Supplier<T> supplier) {
    return transactionTemplate.execute(status -> supplier.get());
  }

  private void runInTransactionWithoutResult(Runnable action) {
    runInTransaction(
        () -> {
          action.run();
          return null;
        });
  }

  private void restoreTenantContext(TenantContext previousContext) {
    if (previousContext == null) {
      TenantContextHolder.clear();
      return;
    }
    TenantContextHolder.set(previousContext);
  }

  private record SetupProvisionContext(
      Long tenantId,
      String tenantCode,
      Long adminUserId,
      String adminUsername,
      String adminRoleName) {}
}
