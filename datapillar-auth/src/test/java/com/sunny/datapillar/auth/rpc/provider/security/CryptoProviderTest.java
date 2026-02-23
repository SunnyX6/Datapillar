package com.sunny.datapillar.auth.rpc.provider.security;

import com.sunny.datapillar.auth.security.keystore.TenantKeyService;
import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.crypto.RsaKeyPairGenerator;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.rpc.security.v1.DecryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.DecryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EncryptRequest;
import com.sunny.datapillar.common.rpc.security.v1.EncryptResult;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyRequest;
import com.sunny.datapillar.common.rpc.security.v1.EnsureTenantKeyResult;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusRequest;
import com.sunny.datapillar.common.rpc.security.v1.GetTenantKeyStatusResult;
import java.security.KeyPair;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoProviderTest {

    @Mock
    private TenantKeyService tenantKeyService;

    @InjectMocks
    private CryptoProvider cryptoProvider;

    @Test
    void ensureTenantKey_shouldReturnSnapshot() {
        when(tenantKeyService.ensureTenantKey("tenant-acme"))
                .thenReturn(new TenantKeyService.TenantKeySnapshot(
                        "tenant-acme", "READY", "v1", "fp-1", "public-key"));

        EnsureTenantKeyResult result = cryptoProvider.ensureTenantKey(
                EnsureTenantKeyRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .build());

        Assertions.assertTrue(result.hasData());
        Assertions.assertFalse(result.hasError());
        Assertions.assertTrue(result.getData().getSuccess());
        Assertions.assertEquals("tenant-acme", result.getData().getTenantCode());
        Assertions.assertEquals("public-key", result.getData().getPublicKeyPem());
        Assertions.assertEquals("v1", result.getData().getKeyVersion());
        Assertions.assertEquals("fp-1", result.getData().getFingerprint());
    }

    @Test
    void getTenantKeyStatus_shouldReturnStatus() {
        when(tenantKeyService.getStatus("tenant-acme"))
                .thenReturn(new TenantKeyService.TenantKeyStatus(
                        true, "tenant-acme", "READY", "v1", "fp-1"));

        GetTenantKeyStatusResult result = cryptoProvider.getTenantKeyStatus(
                GetTenantKeyStatusRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .build());

        Assertions.assertTrue(result.hasData());
        Assertions.assertFalse(result.hasError());
        Assertions.assertTrue(result.getData().getExists());
        Assertions.assertEquals("tenant-acme", result.getData().getTenantCode());
        Assertions.assertEquals("READY", result.getData().getStatus());
        Assertions.assertEquals("v1", result.getData().getKeyVersion());
        Assertions.assertEquals("fp-1", result.getData().getFingerprint());
    }

    @Test
    void encryptAndDecrypt_shouldUseTenantCode() {
        KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
        String publicKeyPem = RsaKeyPairGenerator.toPublicKeyPem(keyPair.getPublic());
        byte[] privatePem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
        when(tenantKeyService.loadPublicKey("tenant-acme")).thenReturn(publicKeyPem);
        when(tenantKeyService.loadPrivateKey("tenant-acme")).thenReturn(privatePem);

        EncryptResult encryptResult = cryptoProvider.encrypt(
                EncryptRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .setPurpose("llm.api_key")
                        .setPlaintext("secret-value")
                        .build());
        Assertions.assertTrue(encryptResult.hasData());
        Assertions.assertFalse(encryptResult.hasError());
        Assertions.assertTrue(encryptResult.getData().getCiphertext().startsWith("ENCv1:"));

        DecryptResult decryptResult = cryptoProvider.decrypt(
                DecryptRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .setPurpose("llm.api_key")
                        .setCiphertext(encryptResult.getData().getCiphertext())
                        .build());
        Assertions.assertTrue(decryptResult.hasData());
        Assertions.assertFalse(decryptResult.hasError());
        Assertions.assertEquals("secret-value", decryptResult.getData().getPlaintext());
    }

    @Test
    void decrypt_shouldReturnBadRequestErrorWhenCiphertextInvalid() {
        KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
        byte[] privatePem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
        when(tenantKeyService.loadPrivateKey("tenant-acme")).thenReturn(privatePem);

        DecryptResult result = cryptoProvider.decrypt(
                DecryptRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .setPurpose("llm.api_key")
                        .setCiphertext("invalid")
                        .build());

        Assertions.assertTrue(result.hasError());
        Assertions.assertEquals(Code.BAD_REQUEST, result.getError().getCode());
        Assertions.assertEquals(ErrorType.CIPHERTEXT_INVALID, result.getError().getType());
    }

    @Test
    void ensureTenantKey_shouldReturnAlreadyExistsError() {
        when(tenantKeyService.ensureTenantKey("tenant-acme"))
                .thenThrow(new AlreadyExistsException(
                        ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                        Map.of("tenantCode", "tenant-acme"),
                        "私钥文件已存在"));

        EnsureTenantKeyResult result = cryptoProvider.ensureTenantKey(
                EnsureTenantKeyRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .build());

        Assertions.assertTrue(result.hasError());
        Assertions.assertEquals(Code.CONFLICT, result.getError().getCode());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, result.getError().getType());
        Assertions.assertEquals("tenant-acme", result.getError().getContextMap().get("tenantCode"));
    }

    @Test
    void ensureTenantKey_shouldReturnConflictError() {
        when(tenantKeyService.ensureTenantKey("tenant-acme"))
                .thenThrow(new ConflictException(
                        ErrorType.TENANT_PUBLIC_KEY_MISSING,
                        Map.of("tenantCode", "tenant-acme"),
                        "租户密钥数据不一致"));

        EnsureTenantKeyResult result = cryptoProvider.ensureTenantKey(
                EnsureTenantKeyRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .build());

        Assertions.assertTrue(result.hasError());
        Assertions.assertEquals(Code.CONFLICT, result.getError().getCode());
        Assertions.assertEquals(ErrorType.TENANT_PUBLIC_KEY_MISSING, result.getError().getType());
    }

    @Test
    void ensureTenantKey_shouldMapUnexpectedExceptionToServiceUnavailableError() {
        when(tenantKeyService.ensureTenantKey("tenant-acme"))
                .thenThrow(new RuntimeException("boom"));

        EnsureTenantKeyResult result = cryptoProvider.ensureTenantKey(
                EnsureTenantKeyRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .build());

        Assertions.assertTrue(result.hasError());
        Assertions.assertEquals(Code.SERVICE_UNAVAILABLE, result.getError().getCode());
        Assertions.assertEquals(ErrorType.KEY_STORAGE_UNAVAILABLE, result.getError().getType());
    }

    @Test
    void getTenantKeyStatus_shouldMapUnexpectedExceptionToServiceUnavailableError() {
        when(tenantKeyService.getStatus("tenant-acme"))
                .thenThrow(new RuntimeException("boom"));

        GetTenantKeyStatusResult result = cryptoProvider.getTenantKeyStatus(
                GetTenantKeyStatusRequest.newBuilder()
                        .setTenantCode("tenant-acme")
                        .build());

        Assertions.assertTrue(result.hasError());
        Assertions.assertEquals(Code.SERVICE_UNAVAILABLE, result.getError().getCode());
        Assertions.assertEquals(ErrorType.KEY_STORAGE_UNAVAILABLE, result.getError().getType());
    }
}
