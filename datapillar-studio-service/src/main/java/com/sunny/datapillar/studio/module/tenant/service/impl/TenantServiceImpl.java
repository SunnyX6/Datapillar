package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.InternalException;
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
    public Long createTenant(TenantDto.Create dto) {
        if (dto == null) {
            throw new BadRequestException("参数错误");
        }
        if (tenantMapper.selectByCode(dto.getCode()) != null) {
            throw new AlreadyExistsException("资源已存在", dto.getCode());
        }

        AuthCryptoRpcClient.TenantKeySnapshot keySnapshot = authCryptoClient.ensureTenantKey(dto.getCode());
        if (keySnapshot == null || !StringUtils.hasText(keySnapshot.publicKeyPem())) {
            throw new InternalException("服务器内部错误");
        }

        Tenant tenant = new Tenant();
        tenant.setCode(dto.getCode());
        tenant.setName(dto.getName());
        tenant.setType(dto.getType());
        tenant.setEncryptPublicKey(keySnapshot.publicKeyPem());
        tenant.setStatus(STATUS_ACTIVE);
        int inserted = tenantMapper.insert(tenant);
        if (inserted == 0 || tenant.getId() == null) {
            throw new InternalException("服务器内部错误");
        }
        return tenant.getId();
    }

    @Override
    public TenantDto.Response getTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new NotFoundException("资源不存在");
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
            throw new NotFoundException("资源不存在");
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
            throw new NotFoundException("资源不存在");
        }
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
    }
}
