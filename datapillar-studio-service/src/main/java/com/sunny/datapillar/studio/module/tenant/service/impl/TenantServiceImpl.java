package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
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
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.module.tenant.util.TenantIdGenerator;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Tenant service implementation. */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

  private static final int STATUS_ACTIVE = 1;
  private static final String LEGACY_PENDING_PUBLIC_KEY_PLACEHOLDER = "PENDING_PUBLIC_KEY";
  private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,63}$");

  private final TenantMapper tenantMapper;
  private final AuthCryptoRpcClient authCryptoClient;
  private final StudioDbExceptionTranslator studioDbExceptionTranslator;
  private final TenantIdGenerator tenantIdGenerator;
  private final TransactionTemplate transactionTemplate;

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
      throw new com.sunny.datapillar.common.exception.BadRequestException("Parameter error");
    }

    TenantProvisionContext context = startTenantProvisioning(dto);
    AuthCryptoRpcClient.TenantKeySnapshot keySnapshot =
        authCryptoClient.ensureTenantKey(context.tenantCode());
    if (keySnapshot == null || !StringUtils.hasText(keySnapshot.publicKeyPem())) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }

    completeTenantProvisioning(context.tenantId(), keySnapshot.publicKeyPem());
    return context.tenantId();
  }

  @Override
  public TenantResponse getTenant(Long tenantId) {
    Tenant tenant = tenantMapper.selectById(tenantId);
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException("Resource does not exist");
    }
    TenantResponse response = new TenantResponse();
    BeanUtils.copyProperties(tenant, response);
    return response;
  }

  @Override
  @org.springframework.transaction.annotation.Transactional
  public void updateTenant(Long tenantId, TenantUpdateRequest dto) {
    Tenant tenant = tenantMapper.selectById(tenantId);
    if (tenant == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException("Resource does not exist");
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
      throw new com.sunny.datapillar.common.exception.NotFoundException("Resource does not exist");
    }
    tenant.setStatus(status);
    tenantMapper.updateById(tenant);
  }

  private TenantProvisionContext startTenantProvisioning(TenantCreateRequest dto) {
    return runInTransaction(
        () -> {
          String tenantCode = normalizeTenantCode(dto.getCode());
          Tenant existing = tenantMapper.selectByCode(tenantCode);
          if (existing == null) {
            Tenant created = insertProvisioningTenant(dto, tenantCode);
            return new TenantProvisionContext(created.getId(), tenantCode);
          }
          if (isAlreadyProvisioned(existing)) {
            throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
                ErrorType.TENANT_CODE_ALREADY_EXISTS,
                Map.of("tenantCode", tenantCode),
                "Tenant code already exists");
          }
          existing.setStatus(STATUS_ACTIVE);
          int updated = tenantMapper.updateById(existing);
          if (updated == 0) {
            throw new com.sunny.datapillar.common.exception.InternalException(
                "Server internal error");
          }
          return new TenantProvisionContext(existing.getId(), tenantCode);
        });
  }

  private Tenant insertProvisioningTenant(TenantCreateRequest dto, String tenantCode) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantIdGenerator.nextId());
    tenant.setCode(tenantCode);
    tenant.setName(dto.getName());
    tenant.setType(dto.getType());
    tenant.setEncryptPublicKey(null);
    tenant.setStatus(STATUS_ACTIVE);

    int inserted;
    try {
      inserted = tenantMapper.insert(tenant);
    } catch (RuntimeException re) {
      throw translateDbException(re, StudioDbScene.STUDIO_TENANT_MANAGE);
    }
    if (inserted == 0 || tenant.getId() == null) {
      throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
    }
    return tenant;
  }

  private void completeTenantProvisioning(Long tenantId, String publicKeyPem) {
    runInTransactionWithoutResult(
        () -> {
          Tenant tenant = tenantMapper.selectById(tenantId);
          if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.InternalException(
                "Server internal error");
          }
          tenant.setEncryptPublicKey(publicKeyPem.trim());
          tenant.setStatus(STATUS_ACTIVE);
          int updated = tenantMapper.updateById(tenant);
          if (updated == 0) {
            throw new com.sunny.datapillar.common.exception.InternalException(
                "Server internal error");
          }
        });
  }

  private boolean isAlreadyProvisioned(Tenant tenant) {
    return hasUsablePublicKey(tenant.getEncryptPublicKey());
  }

  private boolean hasUsablePublicKey(String publicKeyPem) {
    if (!StringUtils.hasText(publicKeyPem)) {
      return false;
    }
    return !LEGACY_PENDING_PUBLIC_KEY_PLACEHOLDER.equals(publicKeyPem.trim());
  }

  private String normalizeTenantCode(String tenantCode) {
    if (!StringUtils.hasText(tenantCode)) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Parameter error");
    }
    String normalized = tenantCode.trim().toLowerCase(Locale.ROOT);
    if (!TENANT_CODE_PATTERN.matcher(normalized).matches()) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "Parameter error", "tenantCode must match ^[a-z0-9][a-z0-9_-]{1,63}$");
    }
    return normalized;
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

  private RuntimeException translateDbException(
      RuntimeException runtimeException, StudioDbScene scene) {
    DbStorageException dbException = SQLExceptionUtils.translate(runtimeException);
    if (dbException == null) {
      return runtimeException;
    }
    return studioDbExceptionTranslator.map(scene, dbException);
  }

  private record TenantProvisionContext(Long tenantId, String tenantCode) {}
}
