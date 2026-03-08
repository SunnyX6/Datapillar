package com.sunny.datapillar.studio.security.keystore;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Key storage properties. */
@Data
@ConfigurationProperties(prefix = "security.key-storage")
public class KeyStorageProperties {

  private String type = "local";

  private Local local = new Local();
  private ObjectStore object = new ObjectStore();

  @Data
  public static class Local {
    private String path = "./data/datapillar/privkeys";
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
