package com.sunny.datapillar.studio.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantKeyCheckTest {

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private AuthCryptoGenericClient authCryptoClient;

    @Test
    void run_shouldPassWhenAllTenantKeysAreReady() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setEncryptPublicKey("public-key");

        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));
        when(authCryptoClient.existsPrivateKey(1L)).thenReturn(true);

        TenantKeyCheck check = new TenantKeyCheck(tenantMapper, authCryptoClient);

        assertDoesNotThrow(() -> check.run(null));
    }

    @Test
    void run_shouldFailWhenTenantKeyPairIncomplete() {
        Tenant tenant1 = new Tenant();
        tenant1.setId(1L);
        tenant1.setEncryptPublicKey(null);

        Tenant tenant2 = new Tenant();
        tenant2.setId(2L);
        tenant2.setEncryptPublicKey("public-key-2");

        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant1, tenant2));
        when(authCryptoClient.existsPrivateKey(1L)).thenReturn(true);
        when(authCryptoClient.existsPrivateKey(2L)).thenReturn(false);

        TenantKeyCheck check = new TenantKeyCheck(tenantMapper, authCryptoClient);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> check.run(null));
        assertTrue(exception.getMessage().contains("missingPublicKeyTenantIds=[1]"));
        assertTrue(exception.getMessage().contains("missingPrivateKeyTenantIds=[2]"));
    }
}
