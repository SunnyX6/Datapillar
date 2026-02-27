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
import java.net.URI;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * ObjectStorage密钥Storage组件
 * 负责ObjectStorage密钥Storage核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ObjectStorageKeyStorage implements KeyStorage {

    private static final String DEFAULT_PREFIX = "privkeys";
    private final String bucket;
    private final String prefix;
    private final S3Client s3Client;

    public ObjectStorageKeyStorage(KeyStorageProperties properties) {
        if (properties == null || properties.getObject() == null) {
            throw new KeyStorageConfigInvalidException("key_storage.object 不能为空");
        }
        KeyStorageProperties.ObjectStore config = properties.getObject();
        this.bucket = normalizeBucket(config.getBucket());
        this.prefix = normalizePrefix(config.getPrefix());
        this.s3Client = buildClient(config);
    }

    @Override
    public void savePrivateKey(String tenantCode, byte[] privateKeyPemBytes) {
        String normalizedTenantCode = validateTenantCode(tenantCode);
        validatePemBytes(privateKeyPemBytes);
        if (existsPrivateKey(normalizedTenantCode)) {
            throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
                    ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                    Map.of("tenantCode", normalizedTenantCode),
                    "私钥文件已存在");
        }

        String privateKey = resolvePrivateKey(normalizedTenantCode);
        PutObjectRequest privateRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(privateKey)
                .contentType("application/x-pem-file")
                .build();
        try {
            s3Client.putObject(privateRequest, RequestBody.fromBytes(privateKeyPemBytes));
        } catch (S3Exception ex) {
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    ex,
                    ErrorType.KEY_STORAGE_UNAVAILABLE,
                    Map.of("tenantCode", normalizedTenantCode),
                    "密钥存储服务不可用");
        }
    }

    @Override
    public byte[] loadPrivateKey(String tenantCode) {
        String normalizedTenantCode = validateTenantCode(tenantCode);
        String key = resolvePrivateKey(normalizedTenantCode);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request);
            return bytes.asByteArray();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new com.sunny.datapillar.common.exception.NotFoundException(
                        ErrorType.TENANT_KEY_NOT_FOUND,
                        Map.of("tenantCode", normalizedTenantCode),
                        "租户密钥不存在");
            }
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
        String normalizedTenantCode = tenantCode.trim();
        if (normalizedTenantCode.contains("/") || normalizedTenantCode.contains("\\") || normalizedTenantCode.contains("..")) {
            return false;
        }
        return objectExists(resolvePrivateKey(normalizedTenantCode));
    }

    private boolean objectExists(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try {
            s3Client.headObject(request);
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new com.sunny.datapillar.common.exception.ServiceUnavailableException(
                    ex,
                    ErrorType.KEY_STORAGE_UNAVAILABLE,
                    Map.of("objectKey", key),
                    "密钥存储服务不可用");
        }
    }

    private S3Client buildClient(KeyStorageProperties.ObjectStore config) {
        AwsCredentialsProvider credentialsProvider = resolveCredentials(config);
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentialsProvider);

        String region = normalizeText(config.getRegion());
        if (region != null) {
            builder.region(Region.of(region));
        }

        String endpoint = normalizeText(config.getEndpoint());
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
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
            throw new KeyStorageConfigInvalidException("key_storage.object.access_key/secret_key 必须同时配置");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private String resolvePrivateKey(String tenantCode) {
        return prefix + "/" + tenantCode + "/private.pem";
    }

    private String normalizeBucket(String bucket) {
        String normalized = normalizeText(bucket);
        if (normalized == null) {
            throw new KeyStorageConfigInvalidException("key_storage.object.bucket 不能为空");
        }
        return normalized;
    }

    private String normalizePrefix(String rawPrefix) {
        String normalized = normalizeText(rawPrefix);
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
            throw new KeyStorageTenantCodeInvalidException("tenantCode 无效");
        }
        String normalized = tenantCode.trim();
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new KeyStorageTenantCodeInvalidException("tenantCode 无效");
        }
        return normalized;
    }

    private void validatePemBytes(byte[] pemBytes) {
        if (pemBytes == null || pemBytes.length == 0) {
            throw new KeyStoragePrivateKeyInvalidException("私钥内容为空");
        }
    }
}
