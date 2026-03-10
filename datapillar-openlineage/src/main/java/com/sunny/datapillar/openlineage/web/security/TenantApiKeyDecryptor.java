package com.sunny.datapillar.openlineage.web.security;

import com.sunny.datapillar.common.crypto.SecretCodec;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.web.security.keystore.KeyStorage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Decrypts tenant model api key with tenant private key. */
@Component
public class TenantApiKeyDecryptor {

  private static final String ENC_PREFIX = "ENCv1:";

  private final KeyStorage keyStorage;

  public TenantApiKeyDecryptor(KeyStorage keyStorage) {
    this.keyStorage = keyStorage;
  }

  public String decryptModelApiKey(String tenantCode, String encryptedApiKey) {
    if (!StringUtils.hasText(tenantCode)) {
      throw new BadRequestException("tenantCode is invalid");
    }
    if (!StringUtils.hasText(encryptedApiKey)) {
      throw new BadRequestException("model apiKey is empty");
    }
    String ciphertext = encryptedApiKey.trim();
    if (!ciphertext.startsWith(ENC_PREFIX)) {
      throw new BadRequestException("model apiKey must use ENCv1 format");
    }

    String tenantKey = tenantCode.trim();

    if (!keyStorage.existsPrivateKey(tenantKey)) {
      throw new BadRequestException("Tenant private key missing: %s", tenantKey);
    }

    try {
      byte[] privateKeyPem = keyStorage.loadPrivateKey(tenantKey);
      String decrypted = SecretCodec.decrypt(privateKeyPem, ciphertext);
      if (!StringUtils.hasText(decrypted)) {
        throw new InternalException("model apiKey decrypt failed");
      }
      return decrypted;
    } catch (RuntimeException ex) {
      throw new BadRequestException(ex, "model apiKey decrypt failed");
    }
  }
}
