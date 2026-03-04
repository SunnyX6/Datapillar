package com.sunny.datapillar.studio.rpc.crypto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.rpc.security.v1.CryptoService;
import com.sunny.datapillar.common.rpc.security.v1.DecryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.DecryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EncryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.EncryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyRequest;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyResult;
import java.util.Map;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthCryptoRpcClientTest {

  @Mock private CryptoService cryptoService;

  @InjectMocks private AuthCryptoRpcClient client;

  @Test
  void ensureTenantKey_shouldMapAlreadyExistsFromRpcError() {
    EnsureTenantKeyResult result =
        EnsureTenantKeyResult.newBuilder()
            .setError(
                com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                    .setCode(Code.CONFLICT)
                    .setType(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS)
                    .setMessage("Private key file already exists")
                    .putAllContext(Map.of("tenantCode", "tenant-acme"))
                    .build())
            .build();
    when(cryptoService.ensureTenantKey(any(EnsureTenantKeyRequest.class))).thenReturn(result);

    AlreadyExistsException ex =
        Assertions.assertThrows(
            AlreadyExistsException.class, () -> client.ensureTenantKey("tenant-acme"));

    Assertions.assertEquals("Private key file already exists: tenant-acme", ex.getMessage());
    Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, ex.getType());
  }

  @Test
  void ensureTenantKey_shouldMapConflictFromRpcError() {
    EnsureTenantKeyResult result =
        EnsureTenantKeyResult.newBuilder()
            .setError(
                com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                    .setCode(Code.CONFLICT)
                    .setType(ErrorType.TENANT_PUBLIC_KEY_MISSING)
                    .setMessage("Tenant key data is inconsistent")
                    .putAllContext(Map.of("tenantCode", "tenant-acme"))
                    .build())
            .build();
    when(cryptoService.ensureTenantKey(any(EnsureTenantKeyRequest.class))).thenReturn(result);

    ConflictException ex =
        Assertions.assertThrows(
            ConflictException.class, () -> client.ensureTenantKey("tenant-acme"));

    Assertions.assertEquals("Tenant key data is inconsistent: tenant-acme", ex.getMessage());
  }

  @Test
  void encrypt_shouldMapNotFoundFromRpcError() {
    EncryptResult result =
        EncryptResult.newBuilder()
            .setError(
                com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                    .setCode(Code.NOT_FOUND)
                    .setType(ErrorType.TENANT_KEY_NOT_FOUND)
                    .setMessage("Tenant key does not exist")
                    .putAllContext(Map.of("tenantCode", "tenant-acme"))
                    .build())
            .build();
    when(cryptoService.encrypt(any(EncryptRequest.class))).thenReturn(result);

    NotFoundException ex =
        Assertions.assertThrows(
            NotFoundException.class, () -> client.encryptLlmApiKey("tenant-acme", "sk-test"));

    Assertions.assertEquals("Tenant key does not exist: tenant-acme", ex.getMessage());
  }

  @Test
  void decrypt_shouldMapTransportRpcExceptionToServiceUnavailable() {
    when(cryptoService.decrypt(any(DecryptRequest.class)))
        .thenThrow(new RpcException("UNKNOWN : transport failed"));

    ServiceUnavailableException ex =
        Assertions.assertThrows(
            ServiceUnavailableException.class,
            () -> client.decryptLlmApiKey("tenant-acme", "ENCv1:xxx"));

    Assertions.assertEquals("Key storage service is unavailable", ex.getMessage());
  }

  @Test
  void decrypt_shouldMapUnknownRpcErrorCodeToServiceUnavailableWhenRetryable() {
    DecryptResult result =
        DecryptResult.newBuilder()
            .setError(
                com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                    .setCode(Code.SERVICE_UNAVAILABLE)
                    .setType("UNKNOWN_TYPE")
                    .setMessage("upstream unavailable")
                    .setRetryable(true)
                    .build())
            .build();
    when(cryptoService.decrypt(any(DecryptRequest.class))).thenReturn(result);

    ServiceUnavailableException ex =
        Assertions.assertThrows(
            ServiceUnavailableException.class,
            () -> client.decryptLlmApiKey("tenant-acme", "ENCv1:xxx"));

    Assertions.assertEquals("upstream unavailable", ex.getMessage());
    Assertions.assertEquals("UNKNOWN_TYPE", ex.getType());
  }
}
