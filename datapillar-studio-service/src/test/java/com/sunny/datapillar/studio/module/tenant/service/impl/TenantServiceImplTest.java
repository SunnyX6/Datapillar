package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private AuthCryptoRpcClient authCryptoClient;

    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantServiceImpl(tenantMapper, authCryptoClient);
    }

    @Test
    void createTenant_shouldEnsureKeyThenInsertActiveTenant() {
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
        when(authCryptoClient.ensureTenantKey("tenant-acme"))
                .thenReturn(new AuthCryptoRpcClient.TenantKeySnapshot(
                        "tenant-acme",
                        "-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----",
                        "v1",
                        "fp-1"));
        when(tenantMapper.insert(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(101L);
            return 1;
        });

        TenantDto.Create dto = new TenantDto.Create();
        dto.setCode("tenant-acme");
        dto.setName("ACME");
        dto.setType("ENTERPRISE");

        Long tenantId = tenantService.createTenant(dto);

        assertEquals(101L, tenantId);
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        verify(authCryptoClient).ensureTenantKey(eq("tenant-acme"));
        verify(tenantMapper, never()).updateById(any(Tenant.class));
        assertEquals("tenant-acme", tenantCaptor.getValue().getCode());
        assertEquals("ACME", tenantCaptor.getValue().getName());
        assertEquals("ENTERPRISE", tenantCaptor.getValue().getType());
        assertEquals("-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----", tenantCaptor.getValue().getEncryptPublicKey());
        assertEquals(1, tenantCaptor.getValue().getStatus());
    }

    @Test
    void updateTenant_shouldOnlyModifyNameAndType() {
        Tenant tenant = new Tenant();
        tenant.setId(101L);
        tenant.setCode("tenant-acme");
        tenant.setName("Old Name");
        tenant.setType("OLD");
        when(tenantMapper.selectById(101L)).thenReturn(tenant);

        TenantDto.Update dto = new TenantDto.Update();
        dto.setName("New Name");
        dto.setType("ENTERPRISE");

        tenantService.updateTenant(101L, dto);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).updateById(tenantCaptor.capture());
        assertEquals("tenant-acme", tenantCaptor.getValue().getCode());
        assertEquals("New Name", tenantCaptor.getValue().getName());
        assertEquals("ENTERPRISE", tenantCaptor.getValue().getType());
    }

    @Test
    void createTenant_shouldPropagatePrivateKeyAlreadyExists() {
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
        when(authCryptoClient.ensureTenantKey("tenant-acme"))
                .thenThrow(new AlreadyExistsException("私钥文件已存在: tenant-acme"));

        TenantDto.Create dto = new TenantDto.Create();
        dto.setCode("tenant-acme");
        dto.setName("ACME");
        dto.setType("ENTERPRISE");

        AlreadyExistsException ex = assertThrows(
                AlreadyExistsException.class,
                () -> tenantService.createTenant(dto));

        assertEquals("私钥文件已存在: tenant-acme", ex.getMessage());
        verify(tenantMapper, never()).insert(any(Tenant.class));
    }
}
