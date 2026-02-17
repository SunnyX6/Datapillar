package com.sunny.datapillar.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 密钥Storage配置属性
 * 承载密钥Storage配置项并完成参数绑定
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@ConfigurationProperties(prefix = "security.key-storage")
public class KeyStorageProperties {

    /**
     * 存储类型：local | object
     */
    private String type = "local";

    private Local local = new Local();

    private ObjectStore object = new ObjectStore();

    @Data
    public static class Local {
        /**
         * 私钥根目录
         */
        private String path = "/data/datapillar/privkeys";
    }

    @Data
    public static class ObjectStore {
        private String endpoint;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String region;
        private String prefix = "privkeys";
    }
}
