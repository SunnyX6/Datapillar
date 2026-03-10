package com.sunny.datapillar.openlineage.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Semantic search request. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

  @NotBlank(message = "query cannot be empty")
  private String query;

  private Integer topK;

  private Double scoreThreshold;

  @Null(message = "/search request must not contain aiModelId")
  private Long aiModelId;

  public SearchRequest(String query, Integer topK, Double scoreThreshold) {
    this(query, topK, scoreThreshold, null);
  }

  public String query() {
    return query;
  }

  public Integer topK() {
    return topK;
  }

  public Double scoreThreshold() {
    return scoreThreshold;
  }

  public Long aiModelId() {
    return aiModelId;
  }
}
