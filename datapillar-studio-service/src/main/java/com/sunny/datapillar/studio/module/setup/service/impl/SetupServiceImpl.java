package com.sunny.datapillar.studio.module.setup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeaturePermission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.setup.dto.SetupInitializeRequest;
import com.sunny.datapillar.studio.module.setup.dto.SetupInitializeResponse;
import com.sunny.datapillar.studio.module.setup.dto.SetupStatusResponse;
import com.sunny.datapillar.studio.module.setup.dto.SetupStepStatus;
import com.sunny.datapillar.studio.module.setup.dto.SetupDto;
import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import com.sunny.datapillar.studio.module.setup.service.SetupService;
import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 初始化服务实现
 * 实现初始化业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class SetupServiceImpl implements SetupService {

    private static final String DEFAULT_TENANT_TYPE = "ENTERPRISE";
    private static final String ADMIN_ROLE_TYPE = "ADMIN";
    private static final String ADMIN_ROLE_NAME = "超级管理员";
    private static final String ADMIN_PERMISSION_CODE = "ADMIN";
    private static final String GRANT_SOURCE_SYSTEM = "SYSTEM";

    private static final int STATUS_ENABLED = 1;
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
    @Transactional
    public SetupInitializeResponse initialize(SetupInitializeRequest request) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }

        SystemBootstrap bootstrap = systemBootstrapMapper.selectByIdForUpdate(SYSTEM_BOOTSTRAP_ID);
        if (bootstrap == null) {
            throw new ServiceUnavailableException("服务不可用", "数据库迁移未完成");
        }
        if (isSetupCompleted(bootstrap)) {
            throw new AlreadyExistsException("资源已存在", "系统已初始化");
        }

        Long tenantId = createTenant(request);
        User adminUser = createAdminUser(tenantId, request);
        TenantContext previousContext = TenantContextHolder.get();
        TenantContext setupContext = new TenantContext(tenantId, adminUser.getId(), tenantId, false);
        TenantContextHolder.set(setupContext);
        try {
            bindTenantUser(tenantId, adminUser.getId());

            Role adminRole = upsertAdminRole(tenantId);
            bindUserRole(tenantId, adminUser.getId(), adminRole.getId());
            grantTenantAndRolePermissions(tenantId, adminUser.getId(), adminRole.getId());

            bootstrap.setSetupCompleted(SETUP_COMPLETED);
            bootstrap.setSetupTenantId(tenantId);
            bootstrap.setSetupAdminUserId(adminUser.getId());
            bootstrap.setSetupTokenHash(null);
            bootstrap.setSetupTokenGeneratedAt(null);
            bootstrap.setSetupCompletedAt(LocalDateTime.now());
            int updated = systemBootstrapMapper.updateById(bootstrap);
            if (updated == 0) {
                throw new InternalException("服务器内部错误");
            }
        } finally {
            restoreTenantContext(previousContext);
        }

        SetupInitializeResponse response = new SetupInitializeResponse();
        response.setTenantId(tenantId);
        response.setUserId(adminUser.getId());
        return response;
    }

    private boolean isSetupCompleted(SystemBootstrap bootstrap) {
        return bootstrap != null
                && bootstrap.getSetupCompleted() != null
                && bootstrap.getSetupCompleted() == SETUP_COMPLETED;
    }

    private List<SetupDto.StepStatus> buildStepStatuses(boolean schemaReady, boolean initialized) {
        return List.of(
                createStepStatus(
                        STEP_SCHEMA_MIGRATION,
                        "数据库迁移",
                        "执行 Flyway 结构与字典迁移",
                        resolveSchemaMigrationStatus(schemaReady)),
                createStepStatus(
                        STEP_SYSTEM_INITIALIZATION,
                        "管理员初始化",
                        "创建首租户、管理员与权限绑定",
                        resolveSystemInitializationStatus(schemaReady, initialized)),
                createStepStatus(
                        STEP_COMPLETED,
                        "初始化完成",
                        "系统进入可登录状态",
                        initialized ? STEP_STATUS_COMPLETED : STEP_STATUS_PENDING));
    }

    private String resolveSchemaMigrationStatus(boolean schemaReady) {
        return schemaReady ? STEP_STATUS_COMPLETED : STEP_STATUS_IN_PROGRESS;
    }

    private String resolveSystemInitializationStatus(boolean schemaReady, boolean initialized) {
        if (!schemaReady) {
            return STEP_STATUS_PENDING;
        }
        if (initialized) {
            return STEP_STATUS_COMPLETED;
        }
        return STEP_STATUS_IN_PROGRESS;
    }

    private SetupDto.StepStatus createStepStatus(String code, String name, String description, String status) {
        SetupStepStatus stepStatus = new SetupStepStatus();
        stepStatus.setCode(code);
        stepStatus.setName(name);
        stepStatus.setDescription(description);
        stepStatus.setStatus(status);
        return stepStatus;
    }

    private Long createTenant(SetupInitializeRequest request) {
        TenantDto.Create tenant = new TenantDto.Create();
        tenant.setCode(resolveTenantCode(request));
        tenant.setName(resolveTenantName(request));
        tenant.setType(DEFAULT_TENANT_TYPE);
        return tenantService.createTenant(tenant);
    }

    private User createAdminUser(Long tenantId, SetupInitializeRequest request) {
        String email = resolveAdminEmail(request);
        String username = resolveAdminUsername(request);

        LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, email)
                .eq(User::getDeleted, USER_NOT_DELETED);
        if (userMapper.selectOne(emailQuery) != null) {
            throw new AlreadyExistsException("资源已存在", email);
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(ARGON2_PASSWORD_ENCODER.encode(resolveAdminPassword(request)));
        user.setNickname(resolveAdminDisplayName(request));
        user.setEmail(email);
        user.setStatus(STATUS_ENABLED);
        user.setDeleted(USER_NOT_DELETED);

        int inserted = userMapper.insert(user);
        if (inserted == 0 || user.getId() == null) {
            throw new InternalException("服务器内部错误");
        }
        return user;
    }

    private void bindTenantUser(Long tenantId, Long userId) {
        TenantUser existing = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
        if (existing != null) {
            LambdaUpdateWrapper<TenantUser> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(TenantUser::getTenantId, tenantId)
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
            existing.setSort(1);
            roleMapper.updateById(existing);
            return existing;
        }

        Role role = new Role();
        role.setTenantId(tenantId);
        role.setType(ADMIN_ROLE_TYPE);
        role.setName(ADMIN_ROLE_NAME);
        role.setDescription("系统内置管理员");
        role.setStatus(STATUS_ENABLED);
        role.setSort(1);
        int inserted = roleMapper.insert(role);
        if (inserted == 0 || role.getId() == null) {
            throw new InternalException("服务器内部错误");
        }
        return role;
    }

    private void bindUserRole(Long tenantId, Long userId, Long roleId) {
        LambdaQueryWrapper<UserRole> query = new LambdaQueryWrapper<>();
        query.eq(UserRole::getTenantId, tenantId)
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
            throw new InternalException("服务器内部错误");
        }

        LambdaQueryWrapper<FeatureObject> activeObjectQuery = new LambdaQueryWrapper<>();
        activeObjectQuery.eq(FeatureObject::getStatus, STATUS_ENABLED)
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
            upsertTenantFeaturePermission(tenantId, object.getId(), adminPermission.getId(), operatorUserId);
            upsertRolePermission(tenantId, roleId, object.getId(), adminPermission.getId());
        }
    }

    private void upsertTenantFeaturePermission(Long tenantId, Long objectId, Long permissionId, Long operatorUserId) {
        TenantFeaturePermission existing = tenantFeaturePermissionMapper.selectByTenantIdAndObjectId(tenantId, objectId);
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
        deleteWrapper.eq(RolePermission::getTenantId, tenantId)
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
        String source = normalizeRequiredText(request.getOrganizationName(), "企业/组织名称不能为空");
        String normalized = normalizeCode(source);
        if (!StringUtils.hasText(normalized)) {
            normalized = Integer.toUnsignedString(source.hashCode(), 36);
        }
        return ensureUniqueTenantCode(applyTenantCodePrefix(normalized));
    }

    private String resolveTenantName(SetupInitializeRequest request) {
        return normalizeRequiredText(request.getOrganizationName(), "企业/组织名称不能为空");
    }

    private String resolveAdminDisplayName(SetupInitializeRequest request) {
        return normalizeRequiredText(request.getAdminName(), "管理员名称不能为空");
    }

    private String resolveAdminEmail(SetupInitializeRequest request) {
        return normalizeEmail(request.getEmail());
    }

    private String resolveAdminPassword(SetupInitializeRequest request) {
        return normalizeRequiredText(request.getPassword(), "管理员密码不能为空");
    }

    private String resolveAdminUsername(SetupInitializeRequest request) {
        String username = normalizeRequiredText(request.getUsername(), "管理员用户名不能为空");
        if (userMapper.selectByUsernameGlobal(username) != null) {
            throw new AlreadyExistsException("资源已存在", username);
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

        throw new AlreadyExistsException("资源已存在", normalizedBase);
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim()
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
            normalized = normalized.substring(0, MAX_TENANT_CODE_LENGTH)
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
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeEmail(String value) {
        String email = normalizeRequiredText(value, "管理员邮箱不能为空");
        return email.toLowerCase(Locale.ROOT);
    }

    private void restoreTenantContext(TenantContext previousContext) {
        if (previousContext == null) {
            TenantContextHolder.clear();
            return;
        }
        TenantContextHolder.set(previousContext);
    }
}
