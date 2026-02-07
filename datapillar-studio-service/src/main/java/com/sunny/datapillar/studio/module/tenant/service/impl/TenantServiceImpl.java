package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.common.utils.KeyCryptoUtil;
import com.sunny.datapillar.studio.security.keystore.KeyStorage;
import java.security.KeyPair;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户服务实现
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final int STATUS_ENABLED = 1;

    private final TenantMapper tenantMapper;
    private final KeyStorage keyStorage;

    @Override
    public IPage<Tenant> listTenants(Integer status, int limit, int offset) {
        long current = resolveCurrent(limit, offset);
        Page<Tenant> page = Page.of(current, limit);
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Tenant::getStatus, status);
        }
        wrapper.orderByAsc(Tenant::getId);
        return tenantMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public Long createTenant(TenantDto.Create dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        if (tenantMapper.selectByCode(dto.getCode()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, dto.getCode());
        }

        Tenant parent = null;
        if (dto.getParentId() != null) {
            parent = tenantMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }

        Tenant tenant = new Tenant();
        tenant.setCode(dto.getCode());
        tenant.setName(dto.getName());
        tenant.setType(dto.getType());
        tenant.setStatus(STATUS_ENABLED);
        tenant.setParentId(dto.getParentId());
        tenant.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        tenant.setPath(parent == null ? "/" : parent.getPath() + "/");

        tenantMapper.insert(tenant);

        String path = buildPath(parent == null ? null : parent.getPath(), tenant.getId());
        tenant.setPath(path);
        tenantMapper.updateById(tenant);

        initializeTenantKey(tenant.getId());

        return tenant.getId();
    }

    private void initializeTenantKey(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String existing = tenant.getEncryptPublicKey();
        if (existing != null && !existing.isBlank()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, tenantId);
        }

        KeyPair keyPair = KeyCryptoUtil.generateRsaKeyPair();
        String publicKeyPem = KeyCryptoUtil.toPublicKeyPem(keyPair.getPublic());
        byte[] privateKeyPem = KeyCryptoUtil.toPrivateKeyPem(keyPair.getPrivate());

        int updated = tenantMapper.updateEncryptPublicKey(tenantId, publicKeyPem);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        try {
            keyStorage.savePrivateKey(tenantId, privateKeyPem);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, ex);
        }
    }

    @Override
    public TenantDto.Response getTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        TenantDto.Response response = new TenantDto.Response();
        BeanUtils.copyProperties(tenant, response);
        return response;
    }

    @Override
    @Transactional
    public void updateTenant(Long tenantId, TenantDto.Update dto) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (dto == null) {
            return;
        }

        if (dto.getName() != null) {
            tenant.setName(dto.getName());
        }
        if (dto.getType() != null) {
            tenant.setType(dto.getType());
        }

        Long targetParentId = dto.getParentId() == null ? tenant.getParentId() : dto.getParentId();
        boolean parentChanged = !Objects.equals(targetParentId, tenant.getParentId());
        if (parentChanged) {
            if (Objects.equals(tenantId, targetParentId)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
            }
            Tenant parent = null;
            if (targetParentId != null) {
                parent = tenantMapper.selectById(targetParentId);
                if (parent == null) {
                    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                }
                if (parent.getPath() != null && tenant.getPath() != null
                        && parent.getPath().startsWith(tenant.getPath())) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
                }
            }
            int newLevel = parent == null ? 1 : parent.getLevel() + 1;
            int delta = newLevel - (tenant.getLevel() == null ? 1 : tenant.getLevel());
            String oldPath = tenant.getPath();
            String newPath = buildPath(parent == null ? null : parent.getPath(), tenant.getId());
            if (oldPath != null && newPath != null && !oldPath.equals(newPath)) {
                tenantMapper.updateHierarchy(oldPath, newPath, delta);
            }
            tenant.setParentId(targetParentId);
            tenant.setLevel(newLevel);
            tenant.setPath(newPath);
        }

        tenantMapper.updateById(tenant);
    }

    @Override
    public void updateStatus(Long tenantId, Integer status) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
    }

    private long resolveCurrent(int limit, int offset) {
        if (limit <= 0) {
            return 1;
        }
        return offset / limit + 1;
    }

    private String buildPath(String parentPath, Long id) {
        String base = parentPath == null ? "" : parentPath.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return "/" + id;
        }
        return base + "/" + id;
    }
}
