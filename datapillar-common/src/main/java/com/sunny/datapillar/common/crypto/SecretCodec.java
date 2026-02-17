package com.sunny.datapillar.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
/**
 * SecretCodec组件
 * 负责SecretCodec核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */

public final class SecretCodec {

    private static final String ENC_PREFIX = "ENCv1:";
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private SecretCodec() {
    }

    public static String encrypt(String publicKeyPem, String plaintext) {
        if (!hasText(publicKeyPem)) {
            throw new IllegalArgumentException("public_key_pem 不能为空");
        }
        if (!hasText(plaintext)) {
            throw new IllegalArgumentException("plaintext 不能为空");
        }

        try {
            PublicKey publicKey = parsePublicKey(publicKeyPem);
            SecretKey aesKey = generateAesKey();
            byte[] nonce = generateNonce();
            byte[] encryptedPayload = encryptWithAesGcm(aesKey, nonce, plaintext);
            byte[] encryptedAesKey = encryptAesKey(publicKey, aesKey.getEncoded());

            byte[] merged = ByteBuffer.allocate(encryptedAesKey.length + nonce.length + encryptedPayload.length)
                    .put(encryptedAesKey)
                    .put(nonce)
                    .put(encryptedPayload)
                    .array();
            return ENC_PREFIX + Base64.getEncoder().encodeToString(merged);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("secret 加密失败", ex);
        }
    }

    public static String decrypt(byte[] privateKeyPem, String encoded) {
        if (!hasText(encoded)) {
            return null;
        }
        if (!hasText(privateKeyPem)) {
            throw new IllegalArgumentException("private_key_pem 不能为空");
        }
        if (!encoded.startsWith(ENC_PREFIX)) {
            throw new IllegalArgumentException("secret 未加密");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded.substring(ENC_PREFIX.length()));
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            int encryptedKeyLength = resolveEncryptedKeyLength(privateKey);
            if (payload.length <= encryptedKeyLength + GCM_NONCE_BYTES) {
                throw new IllegalArgumentException("secret 无效");
            }
            byte[] encryptedAesKey = Arrays.copyOfRange(payload, 0, encryptedKeyLength);
            byte[] nonce = Arrays.copyOfRange(payload, encryptedKeyLength, encryptedKeyLength + GCM_NONCE_BYTES);
            byte[] encryptedPayload = Arrays.copyOfRange(payload, encryptedKeyLength + GCM_NONCE_BYTES, payload.length);

            byte[] aesKeyBytes = decryptAesKey(privateKey, encryptedAesKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            return decryptWithAesGcm(aesKey, nonce, encryptedPayload);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("secret 解密失败", ex);
        }
    }

    public static boolean hasSecret(String value) {
        return hasText(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasText(byte[] value) {
        return value != null && value.length > 0;
    }

    private static PublicKey parsePublicKey(String publicKeyPem) throws Exception {
        String normalized = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] derBytes = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derBytes);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private static PrivateKey parsePrivateKey(byte[] pemBytes) throws Exception {
        String pem = new String(pemBytes, StandardCharsets.US_ASCII);
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] derBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static int resolveEncryptedKeyLength(PrivateKey privateKey) {
        if (privateKey instanceof RSAPrivateKey rsaPrivateKey) {
            return (rsaPrivateKey.getModulus().bitLength() + 7) / 8;
        }
        return 256;
    }

    private static SecretKey generateAesKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    private static byte[] generateNonce() {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private static byte[] encryptWithAesGcm(SecretKey aesKey, byte[] nonce, String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    private static String decryptWithAesGcm(SecretKey aesKey, byte[] nonce, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static byte[] encryptAesKey(PublicKey publicKey, byte[] aesKeyBytes) throws Exception {
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return rsaCipher.doFinal(aesKeyBytes);
    }

    private static byte[] decryptAesKey(PrivateKey privateKey, byte[] encryptedAesKey) throws Exception {
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return rsaCipher.doFinal(encryptedAesKey);
    }
}
