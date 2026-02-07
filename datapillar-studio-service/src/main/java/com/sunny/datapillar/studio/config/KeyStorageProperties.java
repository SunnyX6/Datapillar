package com.sunny.datapillar.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 密钥存储配置
 */
@Data
@Component
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
