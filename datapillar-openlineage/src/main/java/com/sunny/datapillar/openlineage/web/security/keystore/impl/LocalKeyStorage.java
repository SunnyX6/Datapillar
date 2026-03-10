package com.sunny.datapillar.openlineage.web.security.keystore.impl;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.openlineage.web.security.keystore.KeyStorage;
import com.sunny.datapillar.openlineage.web.security.keystore.KeyStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** File-system implementation for private key storage. */
public class LocalKeyStorage implements KeyStorage {

  private final Path basePath;

  public LocalKeyStorage(KeyStorageProperties properties) {
    String path = properties.getLocal() == null ? null : properties.getLocal().getPath();
    if (path == null || path.isBlank()) {
      throw new BadRequestException("security.key-storage.local.path cannot be empty");
    }
    this.basePath = Path.of(path);
  }

  @Override
  public byte[] loadPrivateKey(String tenantCode) {
    String normalizedTenantCode = normalizeTenantCode(tenantCode);
    Path target = resolvePrivatePath(normalizedTenantCode);
    if (!Files.exists(target)) {
      throw new NotFoundException("Tenant key does not exist");
    }

    try {
      return Files.readAllBytes(target);
    } catch (IOException ex) {
      throw new ServiceUnavailableException(ex, "Key storage service is unavailable");
    }
  }

  @Override
  public boolean existsPrivateKey(String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      return false;
    }
    String normalized = tenantCode.trim();
    if (isUnsafeTenantCode(normalized)) {
      return false;
    }
    return Files.exists(resolvePrivatePath(normalized));
  }

  private Path resolvePrivatePath(String tenantCode) {
    return basePath.resolve(tenantCode).resolve("private.pem");
  }

  private String normalizeTenantCode(String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      throw new BadRequestException("tenantCode is invalid");
    }
    String normalized = tenantCode.trim();
    if (isUnsafeTenantCode(normalized)) {
      throw new BadRequestException("tenantCode is invalid");
    }
    return normalized;
  }

  private boolean isUnsafeTenantCode(String tenantCode) {
    return tenantCode.contains("/") || tenantCode.contains("\\") || tenantCode.contains("..");
  }
}
