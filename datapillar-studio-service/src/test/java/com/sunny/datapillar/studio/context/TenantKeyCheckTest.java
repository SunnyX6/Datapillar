package com.sunny.datapillar.studio.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.security.crypto.LocalCryptoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantKeyCheckTest {

  @Mock private TenantMapper tenantMapper;

  @Mock private LocalCryptoService localCryptoService;

  @Test
  void run_shouldPassWhenAllTenantKeysAreReady() {
    Tenant tenant = new Tenant();
    tenant.setId(1L);
    tenant.setCode("tenant-1");
    tenant.setEncryptPublicKey("-----BEGIN PUBLIC KEY-----mock-----END PUBLIC KEY-----");
    tenant.setStatus(1);

    when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));
    when(localCryptoService.getTenantKeyStatus("tenant-1"))
        .thenReturn(
            new LocalCryptoService.TenantKeyStatus(true, "tenant-1", "READY", "v1", "fp-1"));

    TenantKeyCheck check = new TenantKeyCheck(tenantMapper, localCryptoService);

    assertDoesNotThrow(() -> check.run(null));
  }

  @Test
  void run_shouldFailWhenTenantKeyPairIncomplete() {
    Tenant tenant1 = new Tenant();
    tenant1.setId(1L);
    tenant1.setCode(null);
    tenant1.setStatus(1);

    Tenant tenant2 = new Tenant();
    tenant2.setId(2L);
    tenant2.setCode("tenant-2");
    tenant2.setEncryptPublicKey(null);
    tenant2.setStatus(1);

    when(tenantMapper.selectList(any())).thenReturn(List.of(tenant1, tenant2));
    when(localCryptoService.getTenantKeyStatus("tenant-2"))
        .thenReturn(new LocalCryptoService.TenantKeyStatus(false, "tenant-2", "MISSING", "", ""));

    TenantKeyCheck check = new TenantKeyCheck(tenantMapper, localCryptoService);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> check.run(null));
    assertTrue(exception.getMessage().contains("missingTenantCodeTenantIds=[1]"));
    assertTrue(exception.getMessage().contains("missingTenantPublicKeyTenantIds=[2]"));
    assertTrue(exception.getMessage().contains("missingOrInvalidKeyTenantIds=[2]"));
  }

  @Test
  void run_shouldSkipWhenTenantIsNotActive() {
    Tenant tenant = new Tenant();
    tenant.setId(3L);
    tenant.setCode("tenant-3");
    tenant.setEncryptPublicKey(null);
    tenant.setStatus(0);

    when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));

    TenantKeyCheck check = new TenantKeyCheck(tenantMapper, localCryptoService);

    assertDoesNotThrow(() -> check.run(null));
  }
}
