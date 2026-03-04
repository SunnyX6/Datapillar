package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.studio.dto.tenant.request.TenantCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.request.TenantUpdateRequest;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.util.TenantIdGenerator;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
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
  @Mock private AuthCryptoRpcClient authCryptoClient;
  @Mock private StudioDbExceptionTranslator studioDbExceptionTranslator;
  @Mock private TenantIdGenerator tenantIdGenerator;
  @Mock private TransactionTemplate transactionTemplate;

  private TenantServiceImpl tenantService;

  @BeforeEach
  void setUp() {
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
            authCryptoClient,
            studioDbExceptionTranslator,
            tenantIdGenerator,
            transactionTemplate);
  }

  @Test
  void createTenant_shouldInsertProvisioningThenFinalizeActive() {
    Tenant persisted = new Tenant();
    when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
    when(tenantIdGenerator.nextId()).thenReturn(101L);
    when(tenantMapper.insert(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(tenantMapper.selectById(101L)).thenReturn(persisted);
    when(tenantMapper.updateById(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant tenant = invocation.getArgument(0);
              copyTenant(tenant, persisted);
              return 1;
            });
    when(authCryptoClient.ensureTenantKey("tenant-acme"))
        .thenReturn(
            new AuthCryptoRpcClient.TenantKeySnapshot(
                "tenant-acme",
                "-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----",
                "v1",
                "fp-1"));

    TenantCreateRequest dto = new TenantCreateRequest();
    dto.setCode("tenant-acme");
    dto.setName("ACME");
    dto.setType("ENTERPRISE");

    Long tenantId = tenantService.createTenant(dto);

    assertEquals(101L, tenantId);
    verify(authCryptoClient).ensureTenantKey(eq("tenant-acme"));
    assertEquals("tenant-acme", persisted.getCode());
    assertEquals("ACME", persisted.getName());
    assertEquals("ENTERPRISE", persisted.getType());
    assertEquals(
        "-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----", persisted.getEncryptPublicKey());
    assertEquals(1, persisted.getStatus());
  }

  @Test
  void createTenant_shouldResumeFailedProvisionRecord() {
    Tenant existing = new Tenant();
    existing.setId(201L);
    existing.setCode("tenant-acme");
    existing.setName("ACME");
    existing.setType("ENTERPRISE");
    existing.setStatus(1);
    existing.setEncryptPublicKey(null);
    when(tenantMapper.selectByCode("tenant-acme")).thenReturn(existing);
    when(tenantMapper.updateById(any(Tenant.class))).thenReturn(1);
    when(tenantMapper.selectById(201L)).thenReturn(existing);
    when(authCryptoClient.ensureTenantKey("tenant-acme"))
        .thenReturn(
            new AuthCryptoRpcClient.TenantKeySnapshot(
                "tenant-acme",
                "-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----",
                "v1",
                "fp-1"));

    TenantCreateRequest dto = new TenantCreateRequest();
    dto.setCode("tenant-acme");
    dto.setName("ACME");
    dto.setType("ENTERPRISE");

    Long tenantId = tenantService.createTenant(dto);

    assertEquals(201L, tenantId);
    verify(tenantMapper, never()).insert(any(Tenant.class));
  }

  @Test
  void createTenant_shouldRejectWhenCodeAlreadyActive() {
    Tenant existing = new Tenant();
    existing.setId(301L);
    existing.setCode("tenant-acme");
    existing.setEncryptPublicKey("-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----");
    when(tenantMapper.selectByCode("tenant-acme")).thenReturn(existing);

    TenantCreateRequest dto = new TenantCreateRequest();
    dto.setCode("tenant-acme");
    dto.setName("ACME");
    dto.setType("ENTERPRISE");

    AlreadyExistsException ex =
        assertThrows(AlreadyExistsException.class, () -> tenantService.createTenant(dto));

    assertEquals("Tenant code already exists", ex.getMessage());
    verify(authCryptoClient, never()).ensureTenantKey(any());
  }

  @Test
  void createTenant_shouldMarkFailedWhenAuthRpcFails() {
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
    RuntimeException rpcException = new RuntimeException("rpc unavailable");
    when(authCryptoClient.ensureTenantKey("tenant-acme")).thenThrow(rpcException);

    TenantCreateRequest dto = new TenantCreateRequest();
    dto.setCode("tenant-acme");
    dto.setName("ACME");
    dto.setType("ENTERPRISE");

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> tenantService.createTenant(dto));

    assertEquals("rpc unavailable", ex.getMessage());
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
