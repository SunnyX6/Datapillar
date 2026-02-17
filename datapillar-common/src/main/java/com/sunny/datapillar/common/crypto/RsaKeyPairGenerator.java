package com.sunny.datapillar.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Rsa密钥PairGenerator组件
 * 负责Rsa密钥PairGenerator核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class RsaKeyPairGenerator {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;

    private RsaKeyPairGenerator() {
    }

    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("生成 RSA 密钥对失败", ex);
        }
    }

    public static String toPublicKeyPem(PublicKey key) {
        if (key == null) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        byte[] pem = toPem("PUBLIC KEY", key.getEncoded());
        return new String(pem, StandardCharsets.US_ASCII);
    }

    public static byte[] toPrivateKeyPem(PrivateKey key) {
        if (key == null) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        return toPem("PRIVATE KEY", key.getEncoded());
    }

    private static byte[] toPem(String type, byte[] derBytes) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(derBytes);
        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        return pem.getBytes(StandardCharsets.US_ASCII);
    }
}
