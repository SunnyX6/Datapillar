package com.sunny.datapillar.openlineage.source.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** MQ body for rebuild command topic. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RebuildCommand {

  private String rebuildId;
  private Long tenantId;
  private String tenantCode;
  private Long aiModelId;
  private Long bindingRevision;
  private String providerCode;
  private String providerModelId;
  private Integer embeddingDimension;
  private String baseUrl;
  private String apiKeyCiphertext;
  private Long requestedBy;
  private Long requestedAt;

  public String rebuildId() {
    return rebuildId;
  }

  public Long tenantId() {
    return tenantId;
  }

  public String tenantCode() {
    return tenantCode;
  }

  public Long aiModelId() {
    return aiModelId;
  }

  public Long bindingRevision() {
    return bindingRevision;
  }

  public String providerCode() {
    return providerCode;
  }

  public String providerModelId() {
    return providerModelId;
  }

  public Integer embeddingDimension() {
    return embeddingDimension;
  }

  public String baseUrl() {
    return baseUrl;
  }

  public String apiKeyCiphertext() {
    return apiKeyCiphertext;
  }

  public Long requestedBy() {
    return requestedBy;
  }

  public Long requestedAt() {
    return requestedAt;
  }
}
