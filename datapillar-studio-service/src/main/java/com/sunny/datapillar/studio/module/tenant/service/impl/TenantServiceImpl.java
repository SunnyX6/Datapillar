package com.sunny.datapillar.studio.module.tenant.service.impl;

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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 租户服务实现
 * 实现租户业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final int STATUS_ACTIVE = 1;

    private final TenantMapper tenantMapper;
    private final AuthCryptoRpcClient authCryptoClient;
    private final StudioDbExceptionTranslator studioDbExceptionTranslator;

    @Override
    public List<Tenant> listTenants(Integer status) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Tenant::getStatus, status);
        }
        wrapper.orderByAsc(Tenant::getId);
        return tenantMapper.selectList(wrapper);
    }

    @Override
    public Long createTenant(TenantCreateRequest dto) {
        if (dto == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }

        AuthCryptoRpcClient.TenantKeySnapshot keySnapshot = authCryptoClient.ensureTenantKey(dto.getCode());
        if (keySnapshot == null || !StringUtils.hasText(keySnapshot.publicKeyPem())) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }

        Tenant tenant = new Tenant();
        tenant.setCode(dto.getCode());
        tenant.setName(dto.getName());
        tenant.setType(dto.getType());
        tenant.setEncryptPublicKey(keySnapshot.publicKeyPem());
        tenant.setStatus(STATUS_ACTIVE);
        int inserted;
        try {
            inserted = tenantMapper.insert(tenant);
        } catch (RuntimeException re) {
            throw translateDbException(re, StudioDbScene.STUDIO_TENANT_MANAGE);
        }
        if (inserted == 0 || tenant.getId() == null) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }
        return tenant.getId();
    }

    @Override
    public TenantResponse getTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("资源不存在");
        }
        TenantResponse response = new TenantResponse();
        BeanUtils.copyProperties(tenant, response);
        return response;
    }

    @Override
    @Transactional
    public void updateTenant(Long tenantId, TenantUpdateRequest dto) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("资源不存在");
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

        tenantMapper.updateById(tenant);
    }

    @Override
    public void updateStatus(Long tenantId, Integer status) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("资源不存在");
        }
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
    }

    private RuntimeException translateDbException(RuntimeException runtimeException, StudioDbScene scene) {
        DbStorageException dbException = SQLExceptionUtils.translate(runtimeException);
        if (dbException == null) {
            return runtimeException;
        }
        return studioDbExceptionTranslator.map(scene, dbException);
    }
}
