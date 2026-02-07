package com.sunny.datapillar.studio.security.keystore.impl;

import com.sunny.datapillar.studio.config.KeyStorageProperties;
import com.sunny.datapillar.studio.security.keystore.KeyStorage;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 对象存储私钥存储（S3 兼容）
 */
public class ObjectStorageKeyStorage implements KeyStorage {

    private static final String DEFAULT_PREFIX = "privkeys";
    private final String bucket;
    private final String prefix;
    private final S3Client s3Client;

    public ObjectStorageKeyStorage(KeyStorageProperties properties) {
        if (properties == null || properties.getObject() == null) {
            throw new IllegalArgumentException("key_storage.object 不能为空");
        }
        KeyStorageProperties.ObjectStore config = properties.getObject();
        this.bucket = normalizeBucket(config.getBucket());
        this.prefix = normalizePrefix(config.getPrefix());
        this.s3Client = buildClient(config);
    }

    @Override
    public void savePrivateKey(Long tenantId, byte[] pemBytes) {
        validateTenantId(tenantId);
        validatePemBytes(pemBytes);
        if (exists(tenantId)) {
            throw new IllegalStateException("租户私钥已存在: " + tenantId);
        }
        String key = resolveKey(tenantId);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/x-pem-file")
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(pemBytes));
        } catch (S3Exception ex) {
            throw new IllegalStateException("写入私钥失败: " + key, ex);
        }
    }

    @Override
    public byte[] loadPrivateKey(Long tenantId) {
        validateTenantId(tenantId);
        String key = resolveKey(tenantId);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request);
            return bytes.asByteArray();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new IllegalStateException("租户私钥不存在: " + tenantId);
            }
            throw new IllegalStateException("读取私钥失败: " + key, ex);
        }
    }

    @Override
    public boolean exists(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return false;
        }
        String key = resolveKey(tenantId);
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
            throw new IllegalStateException("检查私钥失败: " + key, ex);
        }
    }

    private S3Client buildClient(KeyStorageProperties.ObjectStore config) {
        AwsCredentialsProvider credentialsProvider = resolveCredentials(config);
        S3Client.Builder builder = S3Client.builder()
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
            throw new IllegalArgumentException("key_storage.object.access_key/secret_key 必须同时配置");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private String resolveKey(Long tenantId) {
        return prefix + "/" + tenantId + "/private.pem";
    }

    private String normalizeBucket(String bucket) {
        String normalized = normalizeText(bucket);
        if (normalized == null) {
            throw new IllegalArgumentException("key_storage.object.bucket 不能为空");
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

    private void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 无效");
        }
    }

    private void validatePemBytes(byte[] pemBytes) {
        if (pemBytes == null || pemBytes.length == 0) {
            throw new IllegalArgumentException("私钥内容为空");
        }
    }
}
