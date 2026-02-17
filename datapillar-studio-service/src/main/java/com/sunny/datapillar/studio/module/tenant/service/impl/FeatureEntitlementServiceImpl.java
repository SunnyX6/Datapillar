package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeaturePermission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeatureAuditMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.tenant.service.FeatureEntitlementService;
import com.sunny.datapillar.studio.module.tenant.util.PermissionLevelUtil;
import com.sunny.datapillar.studio.util.UserContextUtil;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.NotFoundException;

/**
 * 功能Entitlement服务实现
 * 实现功能Entitlement业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
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
            throw new NotFoundException("资源不存在");
        }
        return item;
    }

    @Override
    @Transactional
    public void updateEntitlement(Long objectId, FeatureEntitlementDto.UpdateItem item) {
        if (item == null) {
            throw new BadRequestException("参数错误");
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
        Map<Long, Permission> permissionByIdMap = buildPermissionByIdMap(permissionMap);
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
                throw new BadRequestException("参数错误");
            }
            if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
                throw new BadRequestException("参数错误");
            }
            Permission permission = resolvePermission(item, permissionMap, permissionByIdMap);
            FeatureObject featureObject = featureObjectMapper.selectById(item.getObjectId());
            if (featureObject == null) {
                throw new NotFoundException("资源不存在");
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

    private Map<Long, Permission> buildPermissionByIdMap(Map<String, Permission> permissionMap) {
        Map<Long, Permission> map = new HashMap<>();
        if (permissionMap == null) {
            return map;
        }
        for (Permission permission : permissionMap.values()) {
            if (permission != null && permission.getId() != null) {
                map.put(permission.getId(), permission);
            }
        }
        return map;
    }

    private Permission resolvePermission(FeatureEntitlementDto.UpdateItem item,
                                         Map<String, Permission> permissionMap,
                                         Map<Long, Permission> permissionByIdMap) {
        if (item.getPermissionId() != null) {
            Permission permission = permissionByIdMap.get(item.getPermissionId());
            if (permission == null) {
                throw new BadRequestException("参数错误", String.valueOf(item.getPermissionId()));
            }
            return permission;
        }
        String normalizedCode = PermissionLevelUtil.normalizeCode(item.getPermissionCode());
        if (normalizedCode == null) {
            throw new BadRequestException("参数错误");
        }
        Permission permission = permissionMap.get(normalizedCode);
        if (permission == null) {
            throw new BadRequestException("参数错误", normalizedCode);
        }
        return permission;
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
            throw new UnauthorizedException("未授权访问");
        }
        return tenantId;
    }

}
