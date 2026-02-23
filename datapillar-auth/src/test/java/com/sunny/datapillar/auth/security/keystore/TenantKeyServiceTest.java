package com.sunny.datapillar.auth.security.keystore;

import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantKeyServiceTest {

    @Mock
    private KeyStorage keyStorage;
    @Mock
    private TenantMapper tenantMapper;

    @Test
    void ensureTenantKey_shouldGenerateAndReturnSnapshotWhenTenantMissingAndPrivateKeyMissing() throws Exception {
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(false);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        TenantKeyService.TenantKeySnapshot snapshot = service.ensureTenantKey("tenant-acme");

        Assertions.assertEquals("tenant-acme", snapshot.tenantCode());
        Assertions.assertEquals(TenantKeyService.KEY_STATUS_READY, snapshot.status());
        Assertions.assertEquals(TenantKeyService.DEFAULT_KEY_VERSION, snapshot.keyVersion());
        Assertions.assertNotNull(snapshot.publicKeyPem());
        Assertions.assertFalse(snapshot.publicKeyPem().isBlank());
        Assertions.assertNotNull(snapshot.fingerprint());
        Assertions.assertEquals(64, snapshot.fingerprint().length());
        verify(keyStorage).savePrivateKey(eq("tenant-acme"), any());
    }

    @Test
    void ensureTenantKey_shouldThrowAlreadyExistsWhenTenantAndPrivateKeyAlreadyExist() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(true);
        Tenant tenant = new Tenant();
        tenant.setCode("tenant-acme");
        tenant.setEncryptPublicKey("public-key");
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(tenant);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        AlreadyExistsException ex = Assertions.assertThrows(
                AlreadyExistsException.class,
                () -> service.ensureTenantKey("tenant-acme"));

        Assertions.assertEquals(Code.CONFLICT, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, ex.getType());
        Assertions.assertEquals("tenant-acme", ex.getContext().get("tenantCode"));
        verify(keyStorage, never()).savePrivateKey(eq("tenant-acme"), any());
    }

    @Test
    void getStatus_shouldReturnMissingWhenKeyDoesNotExist() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(false);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        TenantKeyService.TenantKeyStatus status = service.getStatus("tenant-acme");

        Assertions.assertFalse(status.exists());
        Assertions.assertEquals("tenant-acme", status.tenantCode());
        Assertions.assertEquals(TenantKeyService.KEY_STATUS_MISSING, status.status());
        Assertions.assertNull(status.keyVersion());
        Assertions.assertNull(status.fingerprint());
    }

    @Test
    void getStatus_shouldReturnReadyWhenKeyExists() throws Exception {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(true);
        Tenant tenant = new Tenant();
        tenant.setCode("tenant-acme");
        tenant.setEncryptPublicKey("public-key");
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(tenant);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        TenantKeyService.TenantKeyStatus status = service.getStatus("tenant-acme");

        Assertions.assertTrue(status.exists());
        Assertions.assertEquals("tenant-acme", status.tenantCode());
        Assertions.assertEquals(TenantKeyService.KEY_STATUS_READY, status.status());
        Assertions.assertEquals(TenantKeyService.DEFAULT_KEY_VERSION, status.keyVersion());
        Assertions.assertEquals(sha256Hex("public-key"), status.fingerprint());
    }

    @Test
    void ensureTenantKey_shouldRejectUnsafeTenantCode() {
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);
        BadRequestException ex = Assertions.assertThrows(BadRequestException.class, () -> service.ensureTenantKey("../tenant"));
        Assertions.assertEquals(Code.BAD_REQUEST, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_KEY_INVALID, ex.getType());
    }

    @Test
    void loadPublicKey_shouldReadFromDatabase() {
        Tenant tenant = new Tenant();
        tenant.setCode("tenant-acme");
        tenant.setEncryptPublicKey("public-key");
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(tenant);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        String publicKey = service.loadPublicKey("tenant-acme");

        Assertions.assertEquals("public-key", publicKey);
    }

    @Test
    void ensureTenantKey_shouldThrowConflictWhenPrivateKeyExistsButPublicKeyMissing() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(true);
        Tenant tenant = new Tenant();
        tenant.setCode("tenant-acme");
        tenant.setEncryptPublicKey(null);
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(tenant);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> service.ensureTenantKey("tenant-acme"));

        Assertions.assertEquals(Code.CONFLICT, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_PUBLIC_KEY_MISSING, ex.getType());
        Assertions.assertEquals("tenant-acme", ex.getContext().get("tenantCode"));
    }

    @Test
    void ensureTenantKey_shouldThrowAlreadyExistsWhenTenantMissingButPrivateKeyExists() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(true);
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        AlreadyExistsException ex = Assertions.assertThrows(
                AlreadyExistsException.class,
                () -> service.ensureTenantKey("tenant-acme"));

        Assertions.assertEquals(Code.CONFLICT, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, ex.getType());
        Assertions.assertEquals("tenant-acme", ex.getContext().get("tenantCode"));
    }

    @Test
    void ensureTenantKey_shouldThrowConflictWhenPublicKeyExistsButPrivateKeyMissing() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(false);
        Tenant tenant = new Tenant();
        tenant.setCode("tenant-acme");
        tenant.setEncryptPublicKey("public-key");
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(tenant);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> service.ensureTenantKey("tenant-acme"));

        Assertions.assertEquals(Code.CONFLICT, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_MISSING, ex.getType());
        Assertions.assertEquals("tenant-acme", ex.getContext().get("tenantCode"));
    }

    @Test
    void getStatus_shouldReturnPublicKeyMissingWhenPrivateKeyExists() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(true);
        Tenant tenant = new Tenant();
        tenant.setCode("tenant-acme");
        tenant.setEncryptPublicKey(null);
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(tenant);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        TenantKeyService.TenantKeyStatus status = service.getStatus("tenant-acme");

        Assertions.assertFalse(status.exists());
        Assertions.assertEquals(ErrorType.TENANT_PUBLIC_KEY_MISSING, status.status());
    }

    @Test
    void getStatus_shouldReturnPrivateKeyAlreadyExistsWhenTenantMissingButPrivateKeyExists() {
        when(keyStorage.existsPrivateKey("tenant-acme")).thenReturn(true);
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        TenantKeyService.TenantKeyStatus status = service.getStatus("tenant-acme");

        Assertions.assertFalse(status.exists());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, status.status());
    }

    @Test
    void loadPublicKey_shouldThrowNotFoundWhenMissing() {
        when(tenantMapper.selectByCode("tenant-acme")).thenReturn(null);
        TenantKeyService service = new TenantKeyService(keyStorage, tenantMapper);

        NotFoundException ex = Assertions.assertThrows(
                NotFoundException.class,
                () -> service.loadPublicKey("tenant-acme"));

        Assertions.assertEquals(Code.NOT_FOUND, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_KEY_NOT_FOUND, ex.getType());
        Assertions.assertEquals("tenant-acme", ex.getContext().get("tenantCode"));
    }

    private String sha256Hex(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(Character.forDigit((b >> 4) & 0xf, 16));
            builder.append(Character.forDigit(b & 0xf, 16));
        }
        return builder.toString();
    }
}
