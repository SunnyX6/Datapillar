package com.sunny.datapillar.auth.security.keystore.impl;

import com.sunny.datapillar.auth.config.KeyStorageProperties;
import com.sunny.datapillar.auth.exception.security.KeyStorageTenantCodeInvalidException;
import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalKeyStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoad_shouldUseTenantCodePath() throws Exception {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getLocal().setPath(tempDir.toString());
        LocalKeyStorage storage = new LocalKeyStorage(properties);

        byte[] privatePem = "private-key".getBytes(StandardCharsets.US_ASCII);
        storage.savePrivateKey("tenant-acme", privatePem);

        Path privatePath = tempDir.resolve("tenant-acme").resolve("private.pem");
        Assertions.assertTrue(Files.exists(privatePath));
        Assertions.assertArrayEquals(privatePem, storage.loadPrivateKey("tenant-acme"));
        Assertions.assertTrue(storage.existsPrivateKey("tenant-acme"));
    }

    @Test
    void save_shouldRejectUnsafeTenantCode() {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getLocal().setPath(tempDir.toString());
        LocalKeyStorage storage = new LocalKeyStorage(properties);

        Assertions.assertThrows(
                KeyStorageTenantCodeInvalidException.class,
                () -> storage.savePrivateKey(
                        "../tenant",
                        "private-key".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void save_shouldThrowAlreadyExistsWhenPrivateKeyFileAlreadyExists() {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getLocal().setPath(tempDir.toString());
        LocalKeyStorage storage = new LocalKeyStorage(properties);
        byte[] privatePem = "private-key".getBytes(StandardCharsets.US_ASCII);
        storage.savePrivateKey("tenant-acme", privatePem);

        AlreadyExistsException ex = Assertions.assertThrows(
                AlreadyExistsException.class,
                () -> storage.savePrivateKey("tenant-acme", privatePem));

        Assertions.assertEquals(Code.CONFLICT, ex.getCode());
        Assertions.assertEquals(ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, ex.getType());
        Assertions.assertEquals("tenant-acme", ex.getContext().get("tenantCode"));
    }
}
