package com.sunny.datapillar.openlineage.web.security.keystore.impl;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.openlineage.web.security.keystore.KeyStorage;
import com.sunny.datapillar.openlineage.web.security.keystore.KeyStorageProperties;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Object storage implementation for private key storage. */
public class ObjectStorageKeyStorage implements KeyStorage {

  private static final String DEFAULT_PREFIX = "privkeys";

  private final String bucket;
  private final String prefix;
  private final S3Client s3Client;

  public ObjectStorageKeyStorage(KeyStorageProperties properties) {
    if (properties == null || properties.getObject() == null) {
      throw new BadRequestException("security.key-storage.object cannot be empty");
    }

    KeyStorageProperties.ObjectStore config = properties.getObject();
    this.bucket = normalizeBucket(config.getBucket());
    this.prefix = normalizePrefix(config.getPrefix());
    this.s3Client = buildClient(config);
  }

  @Override
  public byte[] loadPrivateKey(String tenantCode) {
    String normalizedTenantCode = validateTenantCode(tenantCode);
    String key = resolvePrivateKey(normalizedTenantCode);
    GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

    try {
      ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request);
      return bytes.asByteArray();
    } catch (S3Exception ex) {
      if (ex.statusCode() == 404) {
        throw new NotFoundException("Tenant key does not exist");
      }
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
    return objectExists(resolvePrivateKey(normalized));
  }

  private boolean objectExists(String key) {
    HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(key).build();
    try {
      s3Client.headObject(request);
      return true;
    } catch (S3Exception ex) {
      if (ex.statusCode() == 404) {
        return false;
      }
      throw new ServiceUnavailableException(ex, "Key storage service is unavailable");
    }
  }

  private S3Client buildClient(KeyStorageProperties.ObjectStore config) {
    AwsCredentialsProvider credentialsProvider = resolveCredentials(config);
    S3ClientBuilder builder = S3Client.builder().credentialsProvider(credentialsProvider);

    String region = normalizeText(config.getRegion());
    if (region != null) {
      builder.region(Region.of(region));
    }

    String endpoint = normalizeText(config.getEndpoint());
    if (endpoint != null) {
      builder.endpointOverride(URI.create(endpoint));
      builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }

    return builder.build();
  }

  private AwsCredentialsProvider resolveCredentials(KeyStorageProperties.ObjectStore config) {
    String accessKey = normalizeText(config.getAccessKey());
    String secretKey = normalizeText(config.getSecretKey());
    if (accessKey == null && secretKey == null) {
      return DefaultCredentialsProvider.create();
    }
    if (accessKey == null || secretKey == null) {
      throw new BadRequestException(
          "security.key-storage.object.access-key and secret-key must be configured together");
    }
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
  }

  private String resolvePrivateKey(String tenantCode) {
    return prefix + "/" + tenantCode + "/private.pem";
  }

  private String normalizeBucket(String value) {
    String normalized = normalizeText(value);
    if (normalized == null) {
      throw new BadRequestException("security.key-storage.object.bucket cannot be empty");
    }
    return normalized;
  }

  private String normalizePrefix(String value) {
    String normalized = normalizeText(value);
    if (normalized == null) {
      return DEFAULT_PREFIX;
    }
    String trimmed = normalized;
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.isEmpty() ? DEFAULT_PREFIX : trimmed;
  }

  private String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String validateTenantCode(String tenantCode) {
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
