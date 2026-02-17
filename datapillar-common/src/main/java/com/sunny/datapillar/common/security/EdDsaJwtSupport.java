package com.sunny.datapillar.common.security;

import io.jsonwebtoken.Claims;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * EdDsaJWTSupport组件
 * 负责EdDsaJWTSupport核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class EdDsaJwtSupport {

    private static final String EDDSA_ALGORITHM = "Ed25519";
    private static final String CLASSPATH_PREFIX = "classpath:";

    private EdDsaJwtSupport() {
    }

    public static PrivateKey loadPrivateKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("private key path 不能为空");
        }
        try {
            String pem = readPem(path);
            return parsePrivateKey(pem);
        } catch (IOException ex) {
            throw new IllegalStateException("读取私钥失败: " + path, ex);
        }
    }

    public static PublicKey loadPublicKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("public key path 不能为空");
        }
        try {
            String pem = readPem(path);
            return parsePublicKey(pem);
        } catch (IOException ex) {
            throw new IllegalStateException("读取公钥失败: " + path, ex);
        }
    }

    public static PrivateKey parsePrivateKey(String pem) {
        byte[] der = parsePem(pem, "PRIVATE KEY");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(EDDSA_ALGORITHM);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("解析 Ed25519 私钥失败", ex);
        }
    }

    public static PublicKey parsePublicKey(String pem) {
        byte[] der = parsePem(pem, "PUBLIC KEY");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(EDDSA_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("解析 Ed25519 公钥失败", ex);
        }
    }

    public static List<String> toStringList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String value = item.toString().trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
            return result;
        }
        String single = raw.toString().trim();
        if (single.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(single);
    }

    public static boolean hasAudience(Claims claims, String expectedAudience) {
        if (claims == null || expectedAudience == null || expectedAudience.isBlank()) {
            return false;
        }

        Object audienceObject = claims.get("aud");
        if (audienceObject == null) {
            return false;
        }

        String expected = expectedAudience.trim().toLowerCase(Locale.ROOT);
        if (audienceObject instanceof String aud) {
            return expected.equals(aud.trim().toLowerCase(Locale.ROOT));
        }
        if (audienceObject instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(expected::equals);
        }
        return false;
    }

    private static byte[] parsePem(String pem, String type) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException(type + " PEM 不能为空");
        }
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        String normalized = pem
                .replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(type + " PEM 内容为空");
        }
        return Base64.getDecoder().decode(normalized);
    }

    private static String readPem(String path) throws IOException {
        if (path.startsWith(CLASSPATH_PREFIX)) {
            String resource = path.substring(CLASSPATH_PREFIX.length());
            while (resource.startsWith("/")) {
                resource = resource.substring(1);
            }
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = EdDsaJwtSupport.class.getClassLoader();
            }
            try (InputStream inputStream = classLoader.getResourceAsStream(resource)) {
                if (inputStream == null) {
                    throw new IOException("classpath 资源不存在: " + resource);
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII);
            }
        }

        return Files.readString(Path.of(path), StandardCharsets.US_ASCII);
    }
}
