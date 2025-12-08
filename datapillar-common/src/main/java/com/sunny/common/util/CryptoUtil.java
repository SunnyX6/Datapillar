package com.sunny.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类（纯工具类，无 Spring 依赖）
 * 用于API密钥的加密和解密
 *
 * 设计原则：common 模块不依赖 Spring，secretKey 通过构造函数传入
 *
 * @author sunny
 * @since 2024-01-01
 */
@Slf4j
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    private final String secretKey;
    private final String frontendKey;
    private final String pbkdfSalt;

    /**
     * 构造函数
     *
     * @param secretKey 后端加密密钥（用于数据库存储）
     * @param frontendKey 前端加密密钥（用于解密前端传来的数据，必须与前端一致）
     * @param pbkdfSalt PBKDF2盐值（用于密钥派生，必须与前端一致）
     */
    public CryptoUtil(String secretKey, String frontendKey, String pbkdfSalt) {
        this.secretKey = secretKey;
        this.frontendKey = frontendKey;
        this.pbkdfSalt = pbkdfSalt;
    }

    /**
     * 加密API密钥
     * 
     * @param apiKey 原始API密钥
     * @return 加密后的密钥
     */
    public String encryptApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return apiKey;
        }
        
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("API密钥加密失败", e);
            throw new RuntimeException("API密钥加密失败", e);
        }
    }
    
    /**
     * 解密API密钥
     * 
     * @param encryptedApiKey 加密的API密钥
     * @return 解密后的密钥
     */
    public String decryptApiKey(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.trim().isEmpty()) {
            return encryptedApiKey;
        }
        
        // 检查是否已经是加密的数据
        if (!isEncrypted(encryptedApiKey)) {
            return encryptedApiKey;
        }
        
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] decoded = Base64.getDecoder().decode(encryptedApiKey);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("API密钥解密失败，可能是未加密的数据: {}", e.getMessage());
            // 如果解密失败，可能是未加密的原始数据，直接返回
            return encryptedApiKey;
        }
    }
    
    /**
     * 检查字符串是否是加密的（Base64格式）
     * 
     * @param str 待检查的字符串
     * @return 是否是加密的
     */
    private boolean isEncrypted(String str) {
        try {
            // 检查是否是有效的Base64字符串
            Base64.getDecoder().decode(str);
            // 进一步检查：加密后的数据通常不包含常见的API密钥前缀
            return !str.startsWith("sk-") && !str.startsWith("ak-") && !str.startsWith("claude-");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 脱敏显示API密钥
     * 
     * @param apiKey 原始API密钥
     * @return 脱敏后的密钥
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "";
        }
        
        String trimmedKey = apiKey.trim();
        if (trimmedKey.length() <= 8) {
            return "***";
        }
        
        // 显示前4位和后4位，中间用***替代
        String prefix = trimmedKey.substring(0, 4);
        String suffix = trimmedKey.substring(trimmedKey.length() - 4);
        return prefix + "***" + suffix;
    }
    
    /**
     * 解密前端传来的AES-GCM加密密钥
     * 
     * @param encryptedData 前端加密的Base64字符串
     * @return 解密后的明文密钥
     */
    public String decryptFrontendApiKey(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return encryptedData;
        }
        
        try {
            // 生成与前端相同的密钥
            SecretKey key = generateFrontendKey();
            
            // Base64解码
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            
            // 分离IV和密文（前12字节是IV）
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            
            // 解密
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("前端API密钥解密失败: {}", e.getMessage());
            // 如果解密失败，可能是旧格式的数据，尝试用原有方法解密
            return decryptApiKey(encryptedData);
        }
    }
    
    /**
     * 检查是否为前端AES-GCM加密格式
     * 
     * @param encryptedData 加密数据
     * @return 是否为前端加密格式
     */
    public boolean isFrontendEncrypted(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return false;
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            // 前端AES-GCM加密的数据应该至少包含12字节IV + 16字节认证标签 + 密文
            return decoded.length >= 28;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 生成与前端相同的AES密钥
     * 
     * @return AES密钥
     */
    private SecretKey generateFrontendKey() throws Exception {
        // 使用PBKDF2生成与前端相同的密钥
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
            frontendKey.toCharArray(),
            pbkdfSalt.getBytes(StandardCharsets.UTF_8),
            100000,
            256
        );
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
    
    /**
     * 获取密钥字节数组
     * 
     * @return 密钥字节数组
     */
    private byte[] getKeyBytes() {
        // 确保密钥长度为16字节（128位）
        String key = secretKey;
        if (key.length() < 16) {
            key = String.format("%-16s", key).replace(' ', '0');
        } else if (key.length() > 16) {
            key = key.substring(0, 16);
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }
}