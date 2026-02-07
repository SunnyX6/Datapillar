package com.sunny.datapillar.studio.security.keystore.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sunny.datapillar.studio.config.KeyStorageProperties;
import org.junit.jupiter.api.Test;

class ObjectStorageKeyStorageTest {

    @Test
    void shouldRejectMissingBucket() {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getObject().setBucket(" ");
        assertThatThrownBy(() -> new ObjectStorageKeyStorage(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key_storage.object.bucket");
    }

    @Test
    void shouldRejectPartialCredentials() {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getObject().setBucket("test-bucket");
        properties.getObject().setAccessKey("access");
        assertThatThrownBy(() -> new ObjectStorageKeyStorage(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access_key/secret_key");
    }

    @Test
    void shouldReturnFalseForInvalidTenantId() {
        ObjectStorageKeyStorage storage = createStorage();
        assertThat(storage.exists(0L)).isFalse();
        assertThat(storage.exists(-1L)).isFalse();
        assertThat(storage.exists(null)).isFalse();
    }

    @Test
    void shouldRejectInvalidTenantIdOnSave() {
        ObjectStorageKeyStorage storage = createStorage();
        assertThatThrownBy(() -> storage.savePrivateKey(0L, "pem".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void shouldRejectEmptyPemBytes() {
        ObjectStorageKeyStorage storage = createStorage();
        assertThatThrownBy(() -> storage.savePrivateKey(1L, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("私钥内容为空");
    }

    private ObjectStorageKeyStorage createStorage() {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getObject().setBucket("test-bucket");
        properties.getObject().setRegion("us-east-1");
        return new ObjectStorageKeyStorage(properties);
    }
}
