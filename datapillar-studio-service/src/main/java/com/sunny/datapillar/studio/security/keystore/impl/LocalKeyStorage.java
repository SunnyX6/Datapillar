package com.sunny.datapillar.studio.security.keystore.impl;

import com.sunny.datapillar.studio.config.KeyStorageProperties;
import com.sunny.datapillar.studio.security.keystore.KeyStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 本地文件私钥存储
 */
public class LocalKeyStorage implements KeyStorage {

    private final Path basePath;

    public LocalKeyStorage(KeyStorageProperties properties) {
        String path = properties.getLocal() == null ? null : properties.getLocal().getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("key_storage.local.path 不能为空");
        }
        this.basePath = Path.of(path);
    }

    @Override
    public void savePrivateKey(Long tenantId, byte[] pemBytes) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 无效");
        }
        if (pemBytes == null || pemBytes.length == 0) {
            throw new IllegalArgumentException("私钥内容为空");
        }
        Path target = resolvePath(tenantId);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, pemBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalStateException("写入私钥失败: " + target, ex);
        }
    }

    @Override
    public byte[] loadPrivateKey(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 无效");
        }
        Path target = resolvePath(tenantId);
        if (!Files.exists(target)) {
            throw new IllegalStateException("租户私钥不存在: " + tenantId);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new IllegalStateException("读取私钥失败: " + target, ex);
        }
    }

    @Override
    public boolean exists(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return false;
        }
        Path target = resolvePath(tenantId);
        return Files.exists(target);
    }

    private Path resolvePath(Long tenantId) {
        return basePath.resolve(String.valueOf(tenantId)).resolve("private.pem");
    }
}
