package com.sunny.datapillar.studio.security.crypto;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.crypto.RsaKeyPairGenerator;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.security.keystore.KeyStorage;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Tenant key domain service. */
@Service
@RequiredArgsConstructor
public class TenantKeyService {

  public static final String KEY_STATUS_READY = "READY";
  public static final String KEY_STATUS_MISSING = "MISSING";
  public static final String DEFAULT_KEY_VERSION = "v1";

  private final KeyStorage keyStorage;
  private final TenantMapper tenantMapper;

  public TenantKeySnapshot generateTenantKey(String tenantCode) {
    String normalizedTenantCode = requireTenantCode(tenantCode);
    return generateAndBuildSnapshot(normalizedTenantCode);
  }

  public TenantKeySnapshot loadTenantKey(String tenantCode) {
    String normalizedTenantCode = requireTenantCode(tenantCode);
    Map<String, String> context = tenantContext(normalizedTenantCode);

    Tenant tenant = tenantMapper.selectByCode(normalizedTenantCode);
    if (tenant == null) {
      throw new NotFoundException(ErrorType.TENANT_KEY_NOT_FOUND, context, "Tenant does not exist");
    }

    String publicKeyPem = normalizePublicKey(tenant.getEncryptPublicKey());
    if (!StringUtils.hasText(publicKeyPem)) {
      throw new ConflictException(
          ErrorType.TENANT_PUBLIC_KEY_MISSING, context, "Tenant key data is inconsistent");
    }

    if (!keyStorage.existsPrivateKey(normalizedTenantCode)) {
      throw new ConflictException(
          ErrorType.TENANT_PRIVATE_KEY_MISSING, context, "Tenant key data is inconsistent");
    }

    return buildReadySnapshot(normalizedTenantCode, publicKeyPem);
  }

  public TenantKeyStatus getStatus(String tenantCode) {
    String normalizedTenantCode = requireTenantCode(tenantCode);
    Tenant tenant = tenantMapper.selectByCode(normalizedTenantCode);
    boolean privateKeyExists = keyStorage.existsPrivateKey(normalizedTenantCode);

    if (tenant == null) {
      if (privateKeyExists) {
        return new TenantKeyStatus(
            false, normalizedTenantCode, ErrorType.TENANT_PRIVATE_KEY_ALREADY_EXISTS, null, null);
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
          false, normalizedTenantCode, ErrorType.TENANT_PUBLIC_KEY_MISSING, null, null);
    }
    if (!privateKeyExists) {
      return new TenantKeyStatus(
          false, normalizedTenantCode, ErrorType.TENANT_PRIVATE_KEY_MISSING, null, null);
    }

    TenantKeySnapshot snapshot = buildReadySnapshot(normalizedTenantCode, publicKeyPem);
    return new TenantKeyStatus(
        true,
        snapshot.tenantCode(),
        snapshot.status(),
        snapshot.keyVersion(),
        snapshot.fingerprint());
  }

  public String loadPublicKey(String tenantCode) {
    String normalizedTenantCode = requireTenantCode(tenantCode);
    Tenant tenant = tenantMapper.selectByCode(normalizedTenantCode);
    String publicKeyPem = tenant == null ? null : normalizePublicKey(tenant.getEncryptPublicKey());
    if (!StringUtils.hasText(publicKeyPem)) {
      throw new NotFoundException(
          ErrorType.TENANT_KEY_NOT_FOUND,
          tenantContext(normalizedTenantCode),
          "Tenant key does not exist");
    }
    return publicKeyPem;
  }

  public byte[] loadPrivateKey(String tenantCode) {
    return keyStorage.loadPrivateKey(requireTenantCode(tenantCode));
  }

  public void deleteTenantKey(String tenantCode) {
    keyStorage.deletePrivateKey(requireTenantCode(tenantCode));
  }

  private TenantKeySnapshot generateAndBuildSnapshot(String tenantCode) {
    KeyPair keyPair = RsaKeyPairGenerator.generateRsaKeyPair();
    String publicKeyPem = RsaKeyPairGenerator.toPublicKeyPem(keyPair.getPublic());
    byte[] privateKeyPem = RsaKeyPairGenerator.toPrivateKeyPem(keyPair.getPrivate());
    keyStorage.savePrivateKey(tenantCode, privateKeyPem);
    return buildReadySnapshot(tenantCode, publicKeyPem);
  }

  private TenantKeySnapshot buildReadySnapshot(String tenantCode, String publicKeyPem) {
    return new TenantKeySnapshot(
        tenantCode,
        KEY_STATUS_READY,
        DEFAULT_KEY_VERSION,
        computeFingerprint(publicKeyPem),
        publicKeyPem);
  }

  private String computeFingerprint(String publicKeyPem) {
    if (!StringUtils.hasText(publicKeyPem)) {
      throw new InternalException(ErrorType.TENANT_KEY_INVALID, Map.of(), "Tenant key is invalid");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(publicKeyPem.getBytes(StandardCharsets.US_ASCII));
      return toHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new InternalException(
          ex, ErrorType.TENANT_KEY_INVALID, Map.of(), "Tenant key is invalid");
    }
  }

  private String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(Character.forDigit((value >> 4) & 0xF, 16));
      builder.append(Character.forDigit(value & 0xF, 16));
    }
    return builder.toString();
  }

  private String requireTenantCode(String tenantCode) {
    if (!StringUtils.hasText(tenantCode)) {
      throw new BadRequestException(ErrorType.TENANT_KEY_INVALID, Map.of(), "Parameter error");
    }
    String normalized = tenantCode.trim();
    if (isUnsafeTenantCode(normalized)) {
      throw new BadRequestException(ErrorType.TENANT_KEY_INVALID, Map.of(), "Parameter error");
    }
    return normalized;
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

  private boolean isUnsafeTenantCode(String tenantCode) {
    return tenantCode.contains("/") || tenantCode.contains("\\") || tenantCode.contains("..");
  }

  public record TenantKeySnapshot(
      String tenantCode,
      String status,
      String keyVersion,
      String fingerprint,
      String publicKeyPem) {}

  public record TenantKeyStatus(
      boolean exists, String tenantCode, String status, String keyVersion, String fingerprint) {}
}
