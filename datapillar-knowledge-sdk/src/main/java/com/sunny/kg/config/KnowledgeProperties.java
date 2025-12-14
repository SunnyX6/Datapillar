package com.sunny.kg.config;

import com.sunny.kg.client.KnowledgeClientConfig;
import com.sunny.kg.exception.KnowledgeErrorCode;
import com.sunny.kg.exception.KnowledgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * 配置加载器
 * <p>
 * 支持从 properties 文件加载配置
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class KnowledgeProperties {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeProperties.class);

    private static final String DEFAULT_CONFIG_FILE = "knowledge-sdk.properties";

    private final Properties properties;

    public KnowledgeProperties() {
        this.properties = new Properties();
    }

    /**
     * 从默认配置文件加载
     */
    public static KnowledgeProperties load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    /**
     * 从指定配置文件加载
     */
    public static KnowledgeProperties load(String configFile) {
        KnowledgeProperties props = new KnowledgeProperties();
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(configFile)) {
            if (is != null) {
                props.properties.load(is);
                log.info("加载配置文件: {}", configFile);
            } else {
                log.debug("配置文件不存在: {}", configFile);
            }
        } catch (IOException e) {
            log.warn("加载配置文件失败: {}", e.getMessage());
        }
        return props;
    }

    /**
     * 转换为 ClientConfig
     */
    public KnowledgeClientConfig toClientConfig() {
        KnowledgeClientConfig config = new KnowledgeClientConfig();

        config.setNeo4jUri(getString("knowledge.neo4j.uri", null));
        config.setNeo4jUsername(getString("knowledge.neo4j.username", null));
        config.setNeo4jPassword(getString("knowledge.neo4j.password", null));
        config.setNeo4jDatabase(getString("knowledge.neo4j.database", "neo4j"));
        config.setProducer(getString("knowledge.producer", null));
        config.setAsync(getBoolean("knowledge.async", true));
        config.setBatchSize(getInt("knowledge.batch-size", 100));
        config.setFlushInterval(getDuration("knowledge.flush-interval", Duration.ofSeconds(5)));
        config.setMaxConnectionPoolSize(getInt("knowledge.neo4j.max-pool-size", 50));

        return config;
    }

    /**
     * 验证必要配置
     */
    public void validate() {
        if (getString("knowledge.neo4j.uri", null) == null) {
            throw new KnowledgeException(KnowledgeErrorCode.CONFIG_MISSING, "knowledge.neo4j.uri");
        }
        if (getString("knowledge.neo4j.username", null) == null) {
            throw new KnowledgeException(KnowledgeErrorCode.CONFIG_MISSING, "knowledge.neo4j.username");
        }
        if (getString("knowledge.producer", null) == null) {
            throw new KnowledgeException(KnowledgeErrorCode.CONFIG_MISSING, "knowledge.producer");
        }
    }

    public String getString(String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = System.getenv(key.replace(".", "_").replace("-", "_").toUpperCase());
        }
        return value != null ? value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            // 支持格式: 5s, 100ms, 1m
            if (value.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(value.replace("ms", "")));
            } else if (value.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(value.replace("s", "")));
            } else if (value.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(value.replace("m", "")));
            } else {
                return Duration.ofMillis(Long.parseLong(value));
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
