package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.studio.dto.tenant.request.TenantCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.request.TenantUpdateRequest;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoCatalogService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetalakeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSchemaService;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.util.TenantIdGenerator;
import com.sunny.datapillar.studio.security.crypto.LocalCryptoService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

  @Mock private TenantMapper tenantMapper;
  @Mock private LocalCryptoService localCryptoService;
  @Mock private GravitinoMetalakeService gravitinoMetalakeService;
  @Mock private GravitinoCatalogService gravitinoCatalogService;
  @Mock private GravitinoSchemaService gravitinoSchemaService;
  @Mock private StudioDbExceptionTranslator studioDbExceptionTranslator;
  @Mock private TenantIdGenerator tenantIdGenerator;
  @Mock private TransactionTemplate transactionTemplate;

  private TenantServiceImpl tenantService;

  @BeforeEach
  void setUp() {
    lenient().when(transactionTemplate.getTransactionManager()).thenReturn(null);
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
    tenantService =
        new TenantServiceImpl(
            tenantMapper,
            localCryptoService,
            gravitinoMetalakeService,
            gravitinoCatalogService,
            gravitinoSchemaService,
            studioDbExceptionTranslator,
            tenantIdGenerator,
            transactionTemplate);
  }

  @Test
  void createTenant_shouldInitializeResourcesAndPersistPublicKey() {
    Tenant persisted = new Tenant();
    when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
    when(tenantIdGenerator.nextId()).thenReturn(401L);
    when(tenantMapper.insert(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(tenantMapper.selectById(401L)).thenReturn(persisted);
    when(tenantMapper.updateById(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(gravitinoMetalakeService.createMetalake(any(), any(), any(), eq(null))).thenReturn(true);
    when(gravitinoCatalogService.createCatalogIfAbsent(eq("oneMeta"), any(), eq(null)))
        .thenReturn(true);
    when(gravitinoSchemaService.createSchemaIfAbsent(eq("oneMeta"), eq("OneDS"), any(), eq(null)))
        .thenReturn(true);
    when(localCryptoService.generateTenantKey("tenant-acme"))
        .thenReturn(
            new LocalCryptoService.TenantKeySnapshot(
                "tenant-acme",
                "-----BEGIN PUBLIC KEY-----generated-----END PUBLIC KEY-----",
                "v1",
                "fp-1"));

    TenantCreateRequest request = new TenantCreateRequest();
    request.setCode("tenant-acme");
    request.setName("ACME");
    request.setType("ENTERPRISE");

    Long tenantId = tenantService.createTenant(request);

    assertEquals(401L, tenantId);
    verify(gravitinoMetalakeService)
        .createMetalake(eq("oneMeta"), eq("Datapillar tenant metalake"), any(), eq(null));
    verify(gravitinoCatalogService).createCatalogIfAbsent(eq("oneMeta"), any(), eq(null));
    verify(gravitinoSchemaService)
        .createSchemaIfAbsent(eq("oneMeta"), eq("OneDS"), any(), eq(null));
    assertEquals(
        "-----BEGIN PUBLIC KEY-----generated-----END PUBLIC KEY-----",
        persisted.getEncryptPublicKey());
  }

  @Test
  void createTenant_shouldRollbackTenantWhenMetalakeInitializationFails() {
    Tenant persisted = new Tenant();
    when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
    when(tenantIdGenerator.nextId()).thenReturn(451L);
    when(tenantMapper.insert(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(gravitinoMetalakeService.createMetalake(
            eq("oneMeta"), eq("Datapillar tenant metalake"), any(), eq(null)))
        .thenThrow(new RuntimeException("metalake init failed"));

    TenantCreateRequest request = new TenantCreateRequest();
    request.setCode("tenant-acme");
    request.setName("ACME");
    request.setType("ENTERPRISE");

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> tenantService.createTenant(request));

    assertEquals("metalake init failed", exception.getMessage());
    verify(localCryptoService, never()).generateTenantKey("tenant-acme");
    verify(tenantMapper).deleteById(451L);
  }

  @Test
  void createTenant_shouldLoadExistingKeyWhenPrivateKeyAlreadyExists() {
    Tenant persisted = new Tenant();
    when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
    when(tenantIdGenerator.nextId()).thenReturn(501L);
    when(tenantMapper.insert(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(tenantMapper.selectById(501L)).thenReturn(persisted);
    when(tenantMapper.updateById(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(gravitinoMetalakeService.createMetalake(any(), any(), any(), eq(null))).thenReturn(true);
    when(gravitinoCatalogService.createCatalogIfAbsent(eq("oneMeta"), any(), eq(null)))
        .thenReturn(true);
    when(gravitinoSchemaService.createSchemaIfAbsent(eq("oneMeta"), eq("OneDS"), any(), eq(null)))
        .thenReturn(true);
    when(localCryptoService.generateTenantKey("tenant-acme"))
        .thenThrow(
            new AlreadyExistsException(
                ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                Map.of("tenantCode", "tenant-acme"),
                "Tenant private key already exists"));
    when(localCryptoService.loadTenantKey("tenant-acme"))
        .thenReturn(
            new LocalCryptoService.TenantKeySnapshot(
                "tenant-acme",
                "-----BEGIN PUBLIC KEY-----existing-----END PUBLIC KEY-----",
                "v1",
                "fp-1"));

    TenantCreateRequest request = new TenantCreateRequest();
    request.setCode("tenant-acme");
    request.setName("ACME");
    request.setType("ENTERPRISE");

    Long tenantId = tenantService.createTenant(request);

    assertEquals(501L, tenantId);
    verify(localCryptoService).loadTenantKey("tenant-acme");
    assertEquals(
        "-----BEGIN PUBLIC KEY-----existing-----END PUBLIC KEY-----",
        persisted.getEncryptPublicKey());
  }

  @Test
  void updateTenant_shouldKeepTenantCodeImmutable() {
    Tenant existing = new Tenant();
    existing.setId(11L);
    existing.setCode("tenant-fixed");
    existing.setName("Old");
    existing.setType("OLD_TYPE");
    when(tenantMapper.selectById(11L)).thenReturn(existing);
    when(tenantMapper.updateById(any(Tenant.class))).thenReturn(1);

    TenantUpdateRequest request = new TenantUpdateRequest();
    request.setName("New Name");
    request.setType("NEW_TYPE");

    tenantService.updateTenant(11L, request);

    assertEquals("tenant-fixed", existing.getCode());
    assertEquals("New Name", existing.getName());
    assertEquals("NEW_TYPE", existing.getType());
    verify(tenantMapper).updateById(existing);
  }

  private void copyTenant(Tenant from, Tenant to) {
    to.setId(from.getId());
    to.setCode(from.getCode());
    to.setName(from.getName());
    to.setType(from.getType());
    to.setEncryptPublicKey(from.getEncryptPublicKey());
    to.setStatus(from.getStatus());
  }
}
