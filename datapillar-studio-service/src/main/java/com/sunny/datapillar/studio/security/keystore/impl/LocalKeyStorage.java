package com.sunny.datapillar.studio.security.keystore.impl;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.studio.security.keystore.KeyStorage;
import com.sunny.datapillar.studio.security.keystore.KeyStorageProperties;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

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
  public void savePrivateKey(String tenantCode, byte[] privateKeyPemBytes) {
    String normalizedTenantCode = normalizeTenantCode(tenantCode);
    if (privateKeyPemBytes == null || privateKeyPemBytes.length == 0) {
      throw new BadRequestException("Private key payload is empty");
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
      throw new AlreadyExistsException(
          ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
          Map.of("tenantCode", normalizedTenantCode),
          "Tenant private key already exists");
    } catch (IOException ex) {
      throw new ServiceUnavailableException(
          ex,
          ErrorType.KEY_STORAGE_UNAVAILABLE,
          Map.of("tenantCode", normalizedTenantCode),
          "Key storage service is unavailable");
    }
  }

  @Override
  public byte[] loadPrivateKey(String tenantCode) {
    String normalizedTenantCode = normalizeTenantCode(tenantCode);
    Path target = resolvePrivatePath(normalizedTenantCode);
    if (!Files.exists(target)) {
      throw new NotFoundException(
          ErrorType.TENANT_KEY_NOT_FOUND,
          Map.of("tenantCode", normalizedTenantCode),
          "Tenant key does not exist");
    }

    try {
      return Files.readAllBytes(target);
    } catch (IOException ex) {
      throw new ServiceUnavailableException(
          ex,
          ErrorType.KEY_STORAGE_UNAVAILABLE,
          Map.of("tenantCode", normalizedTenantCode),
          "Key storage service is unavailable");
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

  @Override
  public void deletePrivateKey(String tenantCode) {
    String normalizedTenantCode = normalizeTenantCode(tenantCode);
    Path target = resolvePrivatePath(normalizedTenantCode);
    try {
      Files.deleteIfExists(target);
      Path tenantDir = target.getParent();
      if (tenantDir != null && Files.exists(tenantDir) && Files.isDirectory(tenantDir)) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tenantDir)) {
          if (!stream.iterator().hasNext()) {
            Files.deleteIfExists(tenantDir);
          }
        }
      }
    } catch (IOException ex) {
      throw new ServiceUnavailableException(
          ex,
          ErrorType.KEY_STORAGE_UNAVAILABLE,
          Map.of("tenantCode", normalizedTenantCode),
          "Key storage service is unavailable");
    }
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
