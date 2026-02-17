package com.sunny.datapillar.auth.config;

import com.sunny.datapillar.auth.security.keystore.KeyStorage;
import com.sunny.datapillar.auth.security.keystore.impl.LocalKeyStorage;
import com.sunny.datapillar.auth.security.keystore.impl.ObjectStorageKeyStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 密钥Storage配置
 * 负责密钥Storage配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KeyStorageProperties.class)
public class KeyStorageConfig {

    private final KeyStorageProperties properties;

    @Bean
    public KeyStorage keyStorage() {
        String type = normalizeType(properties.getType());
        return switch (type) {
            case "local" -> new LocalKeyStorage(properties);
            case "object" -> new ObjectStorageKeyStorage(properties);
            default -> throw new IllegalArgumentException("不支持的密钥存储类型: " + type);
        };
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "local";
        }
        String normalized = type.trim().toLowerCase();
        return normalized.isEmpty() ? "local" : normalized;
    }
}
