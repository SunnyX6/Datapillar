package com.sunny.datapillar.auth.security.keystore.impl;

import com.sunny.datapillar.auth.config.KeyStorageProperties;
import com.sunny.datapillar.auth.exception.security.KeyStorageConfigInvalidException;
import com.sunny.datapillar.auth.exception.security.KeyStoragePrivateKeyInvalidException;
import com.sunny.datapillar.auth.exception.security.KeyStorageTenantCodeInvalidException;
import com.sunny.datapillar.auth.security.keystore.KeyStorage;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Local密钥Storage组件
 * 负责Local密钥Storage核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LocalKeyStorage implements KeyStorage {

    private final Path basePath;

    public LocalKeyStorage(KeyStorageProperties properties) {
        String path = properties.getLocal() == null ? null : properties.getLocal().getPath();
        if (path == null || path.isBlank()) {
            throw new KeyStorageConfigInvalidException("key_storage.local.path 不能为空");
        }
        this.basePath = Path.of(path);
    }

    @Override
    public void savePrivateKey(String tenantCode, byte[] privateKeyPemBytes) {
        String normalizedTenantCode = normalizeTenantCode(tenantCode);
        if (privateKeyPemBytes == null || privateKeyPemBytes.length == 0) {
            throw new KeyStoragePrivateKeyInvalidException("私钥内容为空");
        }
        Path privateTarget = resolvePrivatePath(normalizedTenantCode);
        try {
            Files.createDirectories(privateTarget.getParent());
            Files.write(
                    privateTarget,
                    privateKeyPemBytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
                    ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                    Map.of("tenantCode", normalizedTenantCode),
                    "私钥文件已存在");
        } catch (IOException ex) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    ex,
                    ErrorType.KEY_STORAGE_UNAVAILABLE,
                    Map.of("tenantCode", normalizedTenantCode),
                    "密钥存储服务不可用");
        }
    }

    @Override
    public byte[] loadPrivateKey(String tenantCode) {
        String normalizedTenantCode = normalizeTenantCode(tenantCode);
        Path target = resolvePrivatePath(normalizedTenantCode);
        if (!Files.exists(target)) {
            throw new com.sunny.datapillar.common.exception.NotFoundException(
                    ErrorType.TENANT_KEY_NOT_FOUND,
                    Map.of("tenantCode", normalizedTenantCode),
                    "租户密钥不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    ex,
                    ErrorType.KEY_STORAGE_UNAVAILABLE,
                    Map.of("tenantCode", normalizedTenantCode),
                    "密钥存储服务不可用");
        }
    }

    @Override
    public boolean existsPrivateKey(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return false;
        }
        String normalized = tenantCode.trim();
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            return false;
        }
        Path privateTarget = resolvePrivatePath(normalized);
        return Files.exists(privateTarget);
    }

    private Path resolvePrivatePath(String tenantCode) {
        return basePath.resolve(tenantCode).resolve("private.pem");
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new KeyStorageTenantCodeInvalidException("tenantCode 无效");
        }
        String normalized = tenantCode.trim();
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new KeyStorageTenantCodeInvalidException("tenantCode 无效");
        }
        return normalized;
    }
}
