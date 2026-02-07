package com.sunny.datapillar.studio.security.keystore.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sunny.datapillar.studio.config.KeyStorageProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalKeyStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndLoadPrivateKey() {
        KeyStorageProperties properties = new KeyStorageProperties();
        properties.getLocal().setPath(tempDir.toString());
        LocalKeyStorage storage = new LocalKeyStorage(properties);

        byte[] payload = "private-key".getBytes(StandardCharsets.UTF_8);
        storage.savePrivateKey(1L, payload);

        byte[] loaded = storage.loadPrivateKey(1L);
        assertThat(loaded).isEqualTo(payload);
        assertThat(storage.exists(1L)).isTrue();
    }
}
