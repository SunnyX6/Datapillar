package com.sunny.datapillar.auth.security.keystore;

import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.crypto.RsaKeyPairGenerator;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 租户密钥服务
 * 负责租户密钥生成、读取与状态管理
 *
 * @author Sunny
 * @date 2026-02-23
 */
@Service
@RequiredArgsConstructor
public class TenantKeyService {

    public static final String KEY_STATUS_READY = "READY";
    public static final String KEY_STATUS_MISSING = "MISSING";
    public static final String DEFAULT_KEY_VERSION = "v1";

    private final KeyStorage keyStorage;
    private final TenantMapper tenantMapper;

    public TenantKeySnapshot ensureTenantKey(String tenantCode) {
        String normalizedTenantCode = requireTenantCode(tenantCode);
        Map<String, String> context = tenantContext(normalizedTenantCode);
        Tenant tenant = tenantMapper.selectByCode(normalizedTenantCode);
        boolean privateKeyExists = keyStorage.existsPrivateKey(normalizedTenantCode);
        if (tenant == null) {
            if (privateKeyExists) {
                throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
                        ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                        context,
                        "私钥文件已存在");
            }
            return generateAndBuildSnapshot(normalizedTenantCode);
        }

        String publicKeyPem = normalizePublicKey(tenant.getEncryptPublicKey());
        boolean publicKeyExists = StringUtils.hasText(publicKeyPem);
        if (privateKeyExists && publicKeyExists) {
            throw new com.sunny.datapillar.common.exception.AlreadyExistsException(
                    ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                    context,
                    "私钥文件已存在");
        }
        if (privateKeyExists && !publicKeyExists) {
            throw new com.sunny.datapillar.common.exception.ConflictException(
                    ErrorType.TENANT_PUBLIC_KEY_MISSING,
                    context,
                    "租户密钥数据不一致");
        }
        if (publicKeyExists && !privateKeyExists) {
            throw new com.sunny.datapillar.common.exception.ConflictException(
                    ErrorType.TENANT_PRIVATE_KEY_MISSING,
                    context,
                    "租户密钥数据不一致");
        }
        throw new com.sunny.datapillar.common.exception.ConflictException(
                ErrorType.TENANT_PUBLIC_KEY_MISSING,
                context,
                "租户密钥数据不一致");
    }

    public TenantKeyStatus getStatus(String tenantCode) {
        String normalizedTenantCode = requireTenantCode(tenantCode);
        Tenant tenant = tenantMapper.selectByCode(normalizedTenantCode);
        boolean privateKeyExists = keyStorage.existsPrivateKey(normalizedTenantCode);
        if (tenant == null) {
            if (privateKeyExists) {
                return new TenantKeyStatus(
                        false,
                        normalizedTenantCode,
                        ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS,
                        null,
                        null);
            }
            return new TenantKeyStatus(false, normalizedTenantCode, KEY_STATUS_MISSING, null, null);
        }

        String publicKeyPem = normalizePublicKey(tenant.getEncryptPublicKey());
        boolean publicKeyExists = StringUtils.hasText(publicKeyPem);
        if (!privateKeyExists && !publicKeyExists) {
            return new TenantKeyStatus(false, normalizedTenantCode, KEY_STATUS_MISSING, null, null);
        }
        if (privateKeyExists && !publicKeyExists) {
            return new TenantKeyStatus(
                    false,
                    normalizedTenantCode,
                    ErrorType.TENANT_PUBLIC_KEY_MISSING,
                    null,
                    null);
        }
        if (!privateKeyExists) {
            return new TenantKeyStatus(
                    false,
                    normalizedTenantCode,
                    ErrorType.TENANT_PRIVATE_KEY_MISSING,
                    null,
                    null);
        }
        String fingerprint = computeFingerprint(publicKeyPem);
        return new TenantKeyStatus(
                true,
                normalizedTenantCode,
                KEY_STATUS_READY,
                DEFAULT_KEY_VERSION,
                fingerprint);
    }

    public String loadPublicKey(String tenantCode) {
        String normalizedTenantCode = requireTenantCode(tenantCode);
        String publicKeyPem = loadPublicKeyFromDatabase(normalizedTenantCode);
        if (!StringUtils.hasText(publicKeyPem)) {
            throw new com.sunny.datapillar.common.exception.NotFoundException(
                    ErrorType.TENANT_KEY_NOT_FOUND,
                    tenantContext(normalizedTenantCode),
                    "租户密钥不存在");
        }
        return publicKeyPem;
    }

    public byte[] loadPrivateKey(String tenantCode) {
        return keyStorage.loadPrivateKey(requireTenantCode(tenantCode));
    }

    private String requireTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new com.sunny.datapillar.common.exception.BadRequestException(ErrorType.TENANT_KEY_INVALID, Map.of(), "参数错误");
        }
        String normalized = tenantCode.trim();
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new com.sunny.datapillar.common.exception.BadRequestException(ErrorType.TENANT_KEY_INVALID, Map.of(), "参数错误");
        }
        return normalized;
    }

    private String computeFingerprint(String publicKeyPem) {
        if (!StringUtils.hasText(publicKeyPem)) {
            throw new com.sunny.datapillar.common.exception.InternalException(ErrorType.TENANT_KEY_INVALID, Map.of(), "租户密钥无效");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKeyPem.getBytes(StandardCharsets.US_ASCII));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new com.sunny.datapillar.common.exception.InternalException(ex, ErrorType.TENANT_KEY_INVALID, Map.of(), "租户密钥无效");
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }

    private String loadPublicKeyFromDatabase(String tenantCode) {
        Tenant tenant = tenantMapper.selectByCode(tenantCode);
        return tenant == null ? null : normalizePublicKey(tenant.getEncryptPublicKey());
    }

    private TenantKeySnapshot generateAndBuildSnapshot(String tenantCode) {
        KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
        String generatedPublicKeyPem = RsaKeyPairGenerator.toPublicKeyPem(keyPair.getPublic());
        byte[] privateKeyPem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
        keyStorage.savePrivateKey(tenantCode, privateKeyPem);
        String fingerprint = computeFingerprint(generatedPublicKeyPem);
        return new TenantKeySnapshot(
                tenantCode,
                KEY_STATUS_READY,
                DEFAULT_KEY_VERSION,
                fingerprint,
                generatedPublicKeyPem);
    }

    private String normalizePublicKey(String publicKeyPem) {
        if (!StringUtils.hasText(publicKeyPem)) {
            return null;
        }
        return publicKeyPem.trim();
    }

    private Map<String, String> tenantContext(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return Map.of();
        }
        return Map.of("tenantCode", tenantCode);
    }

    public record TenantKeySnapshot(
            String tenantCode,
            String status,
            String keyVersion,
            String fingerprint,
            String publicKeyPem) {
    }

    public record TenantKeyStatus(
            boolean exists,
            String tenantCode,
            String status,
            String keyVersion,
            String fingerprint) {
    }
}
