package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
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
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSystemConstants;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoCatalogService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetalakeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSchemaService;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.module.tenant.util.TenantIdGenerator;
import com.sunny.datapillar.studio.security.crypto.LocalCryptoService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
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
  private final LocalCryptoService localCryptoService;
  private final GravitinoMetalakeService gravitinoMetalakeService;
  private final GravitinoCatalogService gravitinoCatalogService;
  private final GravitinoSchemaService gravitinoSchemaService;
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
    return createTenant(dto, true);
  }

  @Override
  public Long createTenant(TenantCreateRequest dto, boolean initializeMetalakes) {
    if (dto == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException("Parameter error");
    }

    TenantProvisionContext context = startTenantProvisioning(dto);
    KeyProvisionResult keyProvisionResult = null;
    try {
      if (initializeMetalakes) {
        initializeTenantMetalakes(context);
      }
      keyProvisionResult = runInNewTransaction(() -> generateOrLoadTenantKey(context.tenantCode()));
      LocalCryptoService.TenantKeySnapshot keySnapshot = keyProvisionResult.snapshot();
      if (keySnapshot == null || !StringUtils.hasText(keySnapshot.publicKeyPem())) {
        throw new com.sunny.datapillar.common.exception.InternalException("Server internal error");
      }

      completeTenantProvisioning(context.tenantId(), keySnapshot.publicKeyPem());
      return context.tenantId();
    } catch (RuntimeException ex) {
      try {
        rollbackFailedProvisioning(context, keyProvisionResult);
      } catch (RuntimeException rollbackEx) {
        ex.addSuppressed(rollbackEx);
      }
      throw ex;
    }
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
    return runInNewTransaction(
        () -> {
          String tenantCode = normalizeTenantCode(dto.getCode());
          Tenant existing = tenantMapper.selectByCode(tenantCode);
          if (existing == null) {
            Tenant created = insertProvisioningTenant(dto, tenantCode);
            return new TenantProvisionContext(created.getId(), tenantCode, true);
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
          return new TenantProvisionContext(existing.getId(), tenantCode, false);
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
    runInNewTransactionWithoutResult(
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

  private KeyProvisionResult generateOrLoadTenantKey(String tenantCode) {
    try {
      return new KeyProvisionResult(localCryptoService.generateTenantKey(tenantCode), true);
    } catch (AlreadyExistsException ex) {
      if (!ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS.equals(ex.getType())) {
        throw ex;
      }
      return new KeyProvisionResult(localCryptoService.loadTenantKey(tenantCode), false);
    }
  }

  private void rollbackFailedProvisioning(
      TenantProvisionContext context, KeyProvisionResult keyProvisionResult) {
    if (context == null) {
      return;
    }
    if (keyProvisionResult != null && keyProvisionResult.created()) {
      runInNewTransactionWithoutResult(
          () -> localCryptoService.deleteTenantKey(context.tenantCode()));
    }
    if (context.newlyCreated()) {
      runInNewTransactionWithoutResult(() -> tenantMapper.deleteById(context.tenantId()));
    }
  }

  private void initializeTenantMetalakes(TenantProvisionContext context) {
    TenantContext previousContext = TenantContextHolder.get();
    TenantContext targetContext = buildTenantContext(context, previousContext);
    TenantContextHolder.set(targetContext);
    try {
      invokeCreateMetalake(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
          GravitinoSystemConstants.MANAGED_METALAKE_COMMENT);
      invokeCreateSemanticCatalog();
      invokeCreateSemanticSchema();
    } finally {
      restoreTenantContext(previousContext);
    }
  }

  private void invokeCreateMetalake(String metalakeName, String comment) {
    gravitinoMetalakeService.createMetalake(
        metalakeName,
        comment,
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
        null);
  }

  private void invokeCreateSemanticCatalog() {
    CatalogCreateCommand command = new CatalogCreateCommand();
    command.setName(GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS);
    command.setType("DATASET");
    command.setProvider("dataset");
    command.setComment(GravitinoSystemConstants.SEMANTIC_CATALOG_COMMENT);
    command.setProperties(java.util.Map.of());
    gravitinoCatalogService.createCatalogIfAbsent(
        GravitinoSystemConstants.MANAGED_METALAKE_ONE_META, command, null);
  }

  private void invokeCreateSemanticSchema() {
    SchemaCreateCommand command = new SchemaCreateCommand();
    command.setName(GravitinoSystemConstants.SEMANTIC_SCHEMA_ONE_DS);
    command.setComment(GravitinoSystemConstants.SEMANTIC_SCHEMA_COMMENT);
    command.setProperties(java.util.Map.of());
    gravitinoSchemaService.createSchemaIfAbsent(
        GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
        GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS,
        command,
        null);
  }

  private TenantContext buildTenantContext(
      TenantProvisionContext context, TenantContext previousContext) {
    Long actorUserId = previousContext == null ? null : previousContext.getActorUserId();
    Long actorTenantId = previousContext == null ? null : previousContext.getActorTenantId();
    boolean impersonation = previousContext != null && previousContext.isImpersonation();
    return new TenantContext(
        context.tenantId(), context.tenantCode(), actorUserId, actorTenantId, impersonation);
  }

  private void restoreTenantContext(TenantContext previousContext) {
    if (previousContext == null) {
      TenantContextHolder.clear();
      return;
    }
    TenantContextHolder.set(previousContext);
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

  private <T> T runInNewTransaction(Supplier<T> supplier) {
    if (transactionTemplate.getTransactionManager() == null) {
      return runInTransaction(supplier);
    }
    TransactionTemplate requiresNewTemplate =
        new TransactionTemplate(transactionTemplate.getTransactionManager());
    requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return requiresNewTemplate.execute(status -> supplier.get());
  }

  private void runInNewTransactionWithoutResult(Runnable action) {
    runInNewTransaction(
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

  private record TenantProvisionContext(Long tenantId, String tenantCode, boolean newlyCreated) {}

  private record KeyProvisionResult(
      LocalCryptoService.TenantKeySnapshot snapshot, boolean created) {}
}
