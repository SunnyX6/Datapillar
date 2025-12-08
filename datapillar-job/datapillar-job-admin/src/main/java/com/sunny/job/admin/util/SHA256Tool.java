package com.sunny.job.admin.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * SHA256 加密工具类
 *
 * @author sunny
 * @since 2025-12-08
 */
public class SHA256Tool {

    /**
     * SHA256 加密
     */
    public static String sha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA256 encryption failed", e);
        }
    }
}
