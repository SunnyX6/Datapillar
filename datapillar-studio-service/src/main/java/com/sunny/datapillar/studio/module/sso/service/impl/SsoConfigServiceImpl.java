package com.sunny.datapillar.studio.module.sso.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.sso.dto.SsoConfigDto;
import com.sunny.datapillar.studio.module.sso.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.module.sso.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.sso.service.SsoConfigService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSO 配置服务实现
 */
@Service
@RequiredArgsConstructor
public class SsoConfigServiceImpl implements SsoConfigService {

    private static final int STATUS_ENABLED = 1;

    private final TenantSsoConfigMapper tenantSsoConfigMapper;

    @Override
    public List<SsoConfigDto.Response> listConfigs() {
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<TenantSsoConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantSsoConfig::getTenantId, tenantId)
                .orderByDesc(TenantSsoConfig::getUpdatedAt)
                .orderByDesc(TenantSsoConfig::getId);
        List<TenantSsoConfig> configs = tenantSsoConfigMapper.selectList(wrapper);
        List<SsoConfigDto.Response> result = new ArrayList<>();
        for (TenantSsoConfig config : configs) {
            SsoConfigDto.Response response = new SsoConfigDto.Response();
            BeanUtils.copyProperties(config, response);
            result.add(response);
        }
        return result;
    }

    @Override
    @Transactional
    public Long createConfig(SsoConfigDto.Create dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        Long tenantId = getRequiredTenantId();
        String provider = normalizeProvider(dto.getProvider());
        if (provider == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        LambdaQueryWrapper<TenantSsoConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantSsoConfig::getTenantId, tenantId)
                .eq(TenantSsoConfig::getProvider, provider);
        if (tenantSsoConfigMapper.selectOne(wrapper) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, provider);
        }
        TenantSsoConfig config = new TenantSsoConfig();
        config.setTenantId(tenantId);
        config.setProvider(provider);
        config.setBaseUrl(dto.getBaseUrl());
        config.setConfigJson(dto.getConfigJson());
        config.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        tenantSsoConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    @Transactional
    public void updateConfig(Long configId, SsoConfigDto.Update dto) {
        if (configId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        Long tenantId = getRequiredTenantId();
        TenantSsoConfig config = tenantSsoConfigMapper.selectById(configId);
        if (config == null || config.getTenantId() == null || !config.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (dto == null) {
            return;
        }
        if (dto.getBaseUrl() != null) {
            config.setBaseUrl(dto.getBaseUrl());
        }
        if (dto.getConfigJson() != null) {
            config.setConfigJson(dto.getConfigJson());
        }
        if (dto.getStatus() != null) {
            config.setStatus(dto.getStatus());
        }
        tenantSsoConfigMapper.updateById(config);
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return tenantId;
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
