package com.sunny.datapillar.openlineage.source.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload for embedding task command. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingTaskPayload {

  private Long tenantId;
  private String tenantCode;
  private String resourceId;
  private String resourceType;
  private String content;
  private Long targetRevision;
  private EmbeddingTriggerType trigger;
  private String sourceEventMessageId;
  private Long aiModelId;
  private String providerCode;
  private String providerModelId;
  private Integer embeddingDimension;
  private String baseUrl;
  private String apiKeyCiphertext;

  public EmbeddingTaskPayload(String resourceId, String resourceType, String content) {
    this.resourceId = resourceId;
    this.resourceType = resourceType;
    this.content = content;
  }

  public Long tenantId() {
    return tenantId;
  }

  public String tenantCode() {
    return tenantCode;
  }

  public String resourceId() {
    return resourceId;
  }

  public String resourceType() {
    return resourceType;
  }

  public String content() {
    return content;
  }

  public Long targetRevision() {
    return targetRevision;
  }

  public EmbeddingTriggerType trigger() {
    return trigger;
  }

  public String sourceEventMessageId() {
    return sourceEventMessageId;
  }

  public Long aiModelId() {
    return aiModelId;
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
}
