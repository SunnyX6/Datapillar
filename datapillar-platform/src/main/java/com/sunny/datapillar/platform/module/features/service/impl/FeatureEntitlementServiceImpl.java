package com.sunny.datapillar.platform.module.features.service.impl;

import com.sunny.datapillar.platform.context.TenantContextHolder;
import com.sunny.datapillar.platform.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.platform.module.features.entity.FeatureObject;
import com.sunny.datapillar.platform.module.features.entity.Permission;
import com.sunny.datapillar.platform.module.features.entity.TenantFeatureAudit;
import com.sunny.datapillar.platform.module.features.entity.TenantFeaturePermission;
import com.sunny.datapillar.platform.module.features.mapper.FeatureObjectMapper;
import com.sunny.datapillar.platform.module.features.mapper.PermissionMapper;
import com.sunny.datapillar.platform.module.features.mapper.TenantFeatureAuditMapper;
import com.sunny.datapillar.platform.module.features.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.platform.module.features.service.FeatureEntitlementService;
import com.sunny.datapillar.platform.module.features.util.PermissionLevelUtil;
import com.sunny.datapillar.platform.util.UserContextUtil;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户功能授权服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureEntitlementServiceImpl implements FeatureEntitlementService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final FeatureObjectMapper featureObjectMapper;
    private final PermissionMapper permissionMapper;
    private final TenantFeaturePermissionMapper tenantFeaturePermissionMapper;
    private final TenantFeatureAuditMapper tenantFeatureAuditMapper;

    @Override
    public List<FeatureEntitlementDto.Item> listEntitlements() {
        Long tenantId = getRequiredTenantId();
        return featureObjectMapper.selectFeatureEntitlements(tenantId);
    }

    @Override
    public FeatureEntitlementDto.Item getEntitlement(Long objectId) {
        Long tenantId = getRequiredTenantId();
        FeatureEntitlementDto.Item item = featureObjectMapper.selectFeatureEntitlement(tenantId, objectId);
        if (item == null) {
            throw new BusinessException(ErrorCode.ADMIN_RESOURCE_NOT_FOUND);
        }
        return item;
    }

    @Override
    @Transactional
    public void updateEntitlement(Long objectId, FeatureEntitlementDto.UpdateItem item) {
        if (item == null) {
            throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT);
        }
        item.setObjectId(objectId);
        updateEntitlements(List.of(item));
    }

    @Override
    @Transactional
    public void updateEntitlements(List<FeatureEntitlementDto.UpdateItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Long tenantId = getRequiredTenantId();
        Long operatorUserId = UserContextUtil.getRequiredUserId();
        Long operatorTenantId = TenantContextHolder.getActorTenantId() == null
                ? tenantId
                : TenantContextHolder.getActorTenantId();
        Map<String, Permission> permissionMap = buildPermissionMap();
        Map<Long, TenantFeaturePermission> existingMap = new HashMap<>();
        for (TenantFeaturePermission permission : tenantFeaturePermissionMapper.selectByTenantId(tenantId)) {
            existingMap.put(permission.getObjectId(), permission);
        }

        for (FeatureEntitlementDto.UpdateItem item : items) {
            if (item == null || item.getObjectId() == null) {
                continue;
            }
            Integer status = item.getStatus();
            if (status == null) {
                throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT);
            }
            if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
                throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT);
            }
            String normalizedCode = PermissionLevelUtil.normalizeCode(item.getPermissionCode());
            if (normalizedCode == null) {
                throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT);
            }
            Permission permission = permissionMap.get(normalizedCode);
            if (permission == null) {
                throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT, normalizedCode);
            }
            FeatureObject featureObject = featureObjectMapper.selectById(item.getObjectId());
            if (featureObject == null) {
                throw new BusinessException(ErrorCode.ADMIN_RESOURCE_NOT_FOUND);
            }

            TenantFeaturePermission existing = existingMap.get(item.getObjectId());
            if (existing == null) {
                TenantFeaturePermission created = new TenantFeaturePermission();
                created.setTenantId(tenantId);
                created.setObjectId(item.getObjectId());
                created.setPermissionId(permission.getId());
                created.setStatus(status);
                created.setGrantSource("ADMIN");
                created.setGrantedBy(operatorUserId);
                created.setGrantedAt(LocalDateTime.now());
                created.setUpdatedBy(operatorUserId);
                created.setUpdatedAt(LocalDateTime.now());
                tenantFeaturePermissionMapper.insert(created);
                existingMap.put(created.getObjectId(), created);
                writeAudit(tenantId, created.getObjectId(), status == STATUS_ENABLED ? "GRANT" : "REVOKE",
                        null, status, null, permission.getId(), operatorUserId, operatorTenantId);
                continue;
            }

            boolean statusChanged = !status.equals(existing.getStatus());
            boolean permissionChanged = !permission.getId().equals(existing.getPermissionId());
            if (!statusChanged && !permissionChanged) {
                continue;
            }

            TenantFeaturePermission update = new TenantFeaturePermission();
            update.setId(existing.getId());
            update.setPermissionId(permission.getId());
            update.setStatus(status);
            update.setGrantSource("ADMIN");
            update.setUpdatedBy(operatorUserId);
            update.setUpdatedAt(LocalDateTime.now());
            tenantFeaturePermissionMapper.updateById(update);

            String action = resolveAction(statusChanged, permissionChanged, existing.getStatus(), status);
            writeAudit(tenantId, existing.getObjectId(), action,
                    existing.getStatus(), status, existing.getPermissionId(), permission.getId(),
                    operatorUserId, operatorTenantId);
            existing.setStatus(status);
            existing.setPermissionId(permission.getId());
        }

        log.info("Updated tenant feature entitlements: tenantId={}, operatorUserId={}, size={}",
                tenantId, operatorUserId, items.size());
    }

    private Map<String, Permission> buildPermissionMap() {
        List<Permission> permissions = permissionMapper.selectSystemPermissions();
        Map<String, Permission> map = new HashMap<>();
        if (permissions == null) {
            return map;
        }
        for (Permission permission : permissions) {
            if (permission.getCode() == null) {
                continue;
            }
            map.put(permission.getCode().trim().toUpperCase(Locale.ROOT), permission);
        }
        return map;
    }

    private String resolveAction(boolean statusChanged, boolean permissionChanged, Integer beforeStatus, Integer afterStatus) {
        if (statusChanged) {
            if (beforeStatus != null && beforeStatus == STATUS_ENABLED && afterStatus == STATUS_DISABLED) {
                return "DISABLE";
            }
            if (beforeStatus != null && beforeStatus == STATUS_DISABLED && afterStatus == STATUS_ENABLED) {
                return "ENABLE";
            }
        }
        if (permissionChanged) {
            return "UPDATE_PERMISSION";
        }
        return "UPDATE_PERMISSION";
    }

    private void writeAudit(Long tenantId,
                            Long objectId,
                            String action,
                            Integer beforeStatus,
                            Integer afterStatus,
                            Long beforePermissionId,
                            Long afterPermissionId,
                            Long operatorUserId,
                            Long operatorTenantId) {
        TenantFeatureAudit audit = new TenantFeatureAudit();
        audit.setTenantId(tenantId);
        audit.setObjectId(objectId);
        audit.setAction(action);
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(afterStatus);
        audit.setBeforePermissionId(beforePermissionId);
        audit.setAfterPermissionId(afterPermissionId);
        audit.setOperatorUserId(operatorUserId);
        audit.setOperatorTenantId(operatorTenantId);
        audit.setRequestId(UserContextUtil.getTraceId());
        audit.setCreatedAt(LocalDateTime.now());
        tenantFeatureAuditMapper.insert(audit);
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.ADMIN_UNAUTHORIZED);
        }
        return tenantId;
    }
}
