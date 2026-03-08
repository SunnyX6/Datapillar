package com.sunny.datapillar.studio.security.crypto;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.crypto.SecretCodec;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Local crypto service for tenant secrets. */
@Service
@RequiredArgsConstructor
public class LocalCryptoService {

  private final TenantKeyService tenantKeyService;

  public String encryptSsoClientSecret(String tenantCode, String plaintext) {
    return encrypt(tenantCode, plaintext);
  }

  public String decryptSsoClientSecret(String tenantCode, String ciphertext) {
    return decrypt(tenantCode, ciphertext);
  }

  public String encryptLlmApiKey(String tenantCode, String plaintext) {
    return encrypt(tenantCode, plaintext);
  }

  public String decryptLlmApiKey(String tenantCode, String ciphertext) {
    return decrypt(tenantCode, ciphertext);
  }

  public TenantKeySnapshot generateTenantKey(String tenantCode) {
    TenantKeyService.TenantKeySnapshot snapshot = tenantKeyService.generateTenantKey(tenantCode);
    return new TenantKeySnapshot(
        snapshot.tenantCode(),
        snapshot.publicKeyPem(),
        snapshot.keyVersion(),
        snapshot.fingerprint());
  }

  public TenantKeySnapshot loadTenantKey(String tenantCode) {
    TenantKeyService.TenantKeySnapshot snapshot = tenantKeyService.loadTenantKey(tenantCode);
    return new TenantKeySnapshot(
        snapshot.tenantCode(),
        snapshot.publicKeyPem(),
        snapshot.keyVersion(),
        snapshot.fingerprint());
  }

  public void deleteTenantKey(String tenantCode) {
    tenantKeyService.deleteTenantKey(tenantCode);
  }

  public TenantKeyStatus getTenantKeyStatus(String tenantCode) {
    TenantKeyService.TenantKeyStatus status = tenantKeyService.getStatus(tenantCode);
    return new TenantKeyStatus(
        status.exists(),
        status.tenantCode(),
        status.status(),
        status.keyVersion(),
        status.fingerprint());
  }

  private String encrypt(String tenantCode, String plaintext) {
    String validTenantCode = requireTenantCode(tenantCode);
    if (!StringUtils.hasText(plaintext)) {
      throw new BadRequestException("Parameter error");
    }
    try {
      String publicKeyPem = tenantKeyService.loadPublicKey(validTenantCode);
      return SecretCodec.encrypt(publicKeyPem, plaintext);
    } catch (DatapillarRuntimeException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException(
          ex,
          ErrorType.TENANT_KEY_INVALID,
          Map.of("tenantCode", validTenantCode),
          "Parameter error");
    } catch (RuntimeException ex) {
      throw new InternalException(ex, ErrorType.INTERNAL_ERROR, Map.of(), "Server internal error");
    }
  }

  private String decrypt(String tenantCode, String ciphertext) {
    String validTenantCode = requireTenantCode(tenantCode);
    if (!StringUtils.hasText(ciphertext)) {
      throw new BadRequestException("Parameter error");
    }
    try {
      byte[] privateKeyPem = tenantKeyService.loadPrivateKey(validTenantCode);
      String plaintext = SecretCodec.decrypt(privateKeyPem, ciphertext);
      if (!StringUtils.hasText(plaintext)) {
        throw new InternalException("Server internal error");
      }
      return plaintext;
    } catch (DatapillarRuntimeException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException(
          ex,
          ErrorType.CIPHERTEXT_INVALID,
          Map.of("tenantCode", validTenantCode),
          "Parameter error");
    } catch (RuntimeException ex) {
      throw new InternalException(ex, ErrorType.INTERNAL_ERROR, Map.of(), "Server internal error");
    }
  }

  private String requireTenantCode(String tenantCode) {
    if (!StringUtils.hasText(tenantCode)) {
      throw new BadRequestException("Parameter error");
    }
    return tenantCode.trim();
  }

  public record TenantKeySnapshot(
      String tenantCode, String publicKeyPem, String keyVersion, String fingerprint) {}

  public record TenantKeyStatus(
      boolean exists, String tenantCode, String status, String keyVersion, String fingerprint) {}
}
