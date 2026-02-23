package com.sunny.datapillar.studio.rpc.crypto;

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
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthCryptoRpcClientTest {

    @Mock
    private CryptoService cryptoService;

    @InjectMocks
    private AuthCryptoRpcClient client;

    @BeforeEach
    void setUp() throws Exception {
        setField(client, "caller", "datapillar-studio-service-test");
    }

    @Test
    void ensureTenantKey_shouldMapAlreadyExistsFromRpcError() {
        EnsureTenantKeyResult result = EnsureTenantKeyResult.newBuilder()
                .setError(com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                        .setCode(Code.CONFLICT)
                        .setType(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS)
                        .setMessage("私钥文件已存在")
                        .putAllContext(Map.of("tenantCode", "tenant-acme"))
                        .build())
                .build();
        when(cryptoService.ensureTenantKey(any(EnsureTenantKeyRequest.class))).thenReturn(result);

        AlreadyExistsException ex = Assertions.assertThrows(
                AlreadyExistsException.class,
                () -> client.ensureTenantKey("tenant-acme"));

        Assertions.assertEquals("私钥文件已存在: tenant-acme", ex.getMessage());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, ex.getType());
    }

    @Test
    void ensureTenantKey_shouldMapConflictFromRpcError() {
        EnsureTenantKeyResult result = EnsureTenantKeyResult.newBuilder()
                .setError(com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                        .setCode(Code.CONFLICT)
                        .setType(ErrorType.TENANT_PUBLIC_KEY_MISSING)
                        .setMessage("租户密钥数据不一致")
                        .putAllContext(Map.of("tenantCode", "tenant-acme"))
                        .build())
                .build();
        when(cryptoService.ensureTenantKey(any(EnsureTenantKeyRequest.class))).thenReturn(result);

        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> client.ensureTenantKey("tenant-acme"));

        Assertions.assertEquals("租户密钥数据不一致: tenant-acme", ex.getMessage());
    }

    @Test
    void encrypt_shouldMapNotFoundFromRpcError() {
        EncryptResult result = EncryptResult.newBuilder()
                .setError(com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                        .setCode(Code.NOT_FOUND)
                        .setType(ErrorType.TENANT_KEY_NOT_FOUND)
                        .setMessage("租户密钥不存在")
                        .putAllContext(Map.of("tenantCode", "tenant-acme"))
                        .build())
                .build();
        when(cryptoService.encrypt(any(EncryptRequest.class))).thenReturn(result);

        NotFoundException ex = Assertions.assertThrows(
                NotFoundException.class,
                () -> client.encryptLlmApiKey("tenant-acme", "sk-test"));

        Assertions.assertEquals("租户密钥不存在: tenant-acme", ex.getMessage());
    }

    @Test
    void decrypt_shouldMapTransportRpcExceptionToServiceUnavailable() {
        when(cryptoService.decrypt(any(DecryptRequest.class)))
                .thenThrow(new RpcException("UNKNOWN : transport failed"));

        ServiceUnavailableException ex = Assertions.assertThrows(
                ServiceUnavailableException.class,
                () -> client.decryptLlmApiKey("tenant-acme", "ENCv1:xxx"));

        Assertions.assertEquals("密钥存储服务不可用", ex.getMessage());
    }

    @Test
    void decrypt_shouldMapUnknownRpcErrorCodeToServiceUnavailableWhenRetryable() {
        DecryptResult result = DecryptResult.newBuilder()
                .setError(com.sunny.datapillar.common.rpc.security.v1.Error.newBuilder()
                        .setCode(Code.SERVICE_UNAVAILABLE)
                        .setType("UNKNOWN_TYPE")
                        .setMessage("upstream unavailable")
                        .setRetryable(true)
                        .build())
                .build();
        when(cryptoService.decrypt(any(DecryptRequest.class))).thenReturn(result);

        ServiceUnavailableException ex = Assertions.assertThrows(
                ServiceUnavailableException.class,
                () -> client.decryptLlmApiKey("tenant-acme", "ENCv1:xxx"));

        Assertions.assertEquals("upstream unavailable", ex.getMessage());
        Assertions.assertEquals("UNKNOWN_TYPE", ex.getType());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
