package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
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
    private AuthCryptoGenericClient authCryptoClient;

    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantServiceImpl(tenantMapper, authCryptoClient);
    }

    @Test
    void createTenant_shouldInsertOnlyOnceAndNotDoSecondUpdate() {
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
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
        verify(tenantMapper, never()).updateById(any(Tenant.class));
        verify(authCryptoClient).savePrivateKey(eq(101L), any(String.class));
        assertEquals("tenant-acme", tenantCaptor.getValue().getCode());
        assertEquals("ACME", tenantCaptor.getValue().getName());
        assertEquals("ENTERPRISE", tenantCaptor.getValue().getType());
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
}
