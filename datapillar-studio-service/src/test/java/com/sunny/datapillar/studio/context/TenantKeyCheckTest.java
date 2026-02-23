package com.sunny.datapillar.studio.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
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
    private AuthCryptoRpcClient authCryptoClient;

    @Test
    void run_shouldPassWhenAllTenantKeysAreReady() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setCode("tenant-1");
        tenant.setEncryptPublicKey("-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----");

        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));
        when(authCryptoClient.getTenantKeyStatus("tenant-1"))
                .thenReturn(new AuthCryptoRpcClient.TenantKeyStatus(
                        true, "tenant-1", "READY", "v1", "fp-1"));

        TenantKeyCheck check = new TenantKeyCheck(tenantMapper, authCryptoClient);

        assertDoesNotThrow(() -> check.run(null));
    }

    @Test
    void run_shouldFailWhenTenantKeyPairIncomplete() {
        Tenant tenant1 = new Tenant();
        tenant1.setId(1L);
        tenant1.setCode(null);

        Tenant tenant2 = new Tenant();
        tenant2.setId(2L);
        tenant2.setCode("tenant-2");
        tenant2.setEncryptPublicKey(null);

        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant1, tenant2));
        when(authCryptoClient.getTenantKeyStatus("tenant-2"))
                .thenReturn(new AuthCryptoRpcClient.TenantKeyStatus(
                        false, "tenant-2", "MISSING", "", ""));

        TenantKeyCheck check = new TenantKeyCheck(tenantMapper, authCryptoClient);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> check.run(null));
        assertTrue(exception.getMessage().contains("missingTenantCodeTenantIds=[1]"));
        assertTrue(exception.getMessage().contains("missingTenantPublicKeyTenantIds=[2]"));
        assertTrue(exception.getMessage().contains("missingOrInvalidKeyTenantIds=[2]"));
    }
}
