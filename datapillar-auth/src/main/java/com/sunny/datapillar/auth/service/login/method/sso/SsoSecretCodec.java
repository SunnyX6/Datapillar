package com.sunny.datapillar.auth.service.login.method.sso;

import com.sunny.datapillar.auth.security.keystore.TenantKeyService;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.InternalException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 单点登录SecretCodec组件
 * 负责单点登录SecretCodec核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class SsoSecretCodec {

    private static final String ENC_PREFIX = "ENCv1:";
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final TenantKeyService tenantKeyService;

    public String decryptSecret(String tenantCode, String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        if (!encoded.startsWith(ENC_PREFIX)) {
            throw new InternalException("SSO配置无效: %s", "clientSecret");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded.substring(ENC_PREFIX.length()));
            PrivateKey privateKey = parsePrivateKey(loadTenantPrivateKey(tenantCode));
            int encryptedKeyLength = resolveEncryptedKeyLength(privateKey);
            if (payload.length <= encryptedKeyLength + GCM_NONCE_BYTES) {
                throw new InternalException("SSO配置无效: %s", "clientSecret");
            }
            byte[] encryptedAesKey = Arrays.copyOfRange(payload, 0, encryptedKeyLength);
            byte[] nonce = Arrays.copyOfRange(payload, encryptedKeyLength, encryptedKeyLength + GCM_NONCE_BYTES);
            byte[] encryptedPayload = Arrays.copyOfRange(payload, encryptedKeyLength + GCM_NONCE_BYTES, payload.length);

            byte[] aesKeyBytes = decryptAesKey(privateKey, encryptedAesKey);
            return decryptWithAesGcm(aesKeyBytes, nonce, encryptedPayload);
        } catch (DatapillarRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalException(ex, "SSO配置无效: %s", "clientSecret");
        }
    }

    private byte[] loadTenantPrivateKey(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new BadRequestException("参数错误");
        }
        try {
            byte[] privateKey = tenantKeyService.loadPrivateKey(tenantCode.trim());
            if (privateKey == null || privateKey.length == 0) {
                throw new InternalException("SSO配置无效: %s", "tenant_private_key_missing");
            }
            return privateKey;
        } catch (DatapillarRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalException(ex, "SSO配置无效: %s", "tenant_private_key_missing");
        }
    }

    private PrivateKey parsePrivateKey(byte[] pemBytes) throws Exception {
        String pem = new String(pemBytes, StandardCharsets.US_ASCII);
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] derBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private int resolveEncryptedKeyLength(PrivateKey privateKey) {
        if (privateKey instanceof RSAPrivateKey rsaPrivateKey) {
            return (rsaPrivateKey.getModulus().bitLength() + 7) / 8;
        }
        return 256;
    }

    private byte[] decryptAesKey(PrivateKey privateKey, byte[] encryptedAesKey) throws Exception {
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return rsaCipher.doFinal(encryptedAesKey);
    }

    private String decryptWithAesGcm(byte[] aesKeyBytes, byte[] nonce, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), spec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
