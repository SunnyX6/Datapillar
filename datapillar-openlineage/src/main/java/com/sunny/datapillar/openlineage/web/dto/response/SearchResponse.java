package com.sunny.datapillar.openlineage.web.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Search response payload. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

  private Long tenantId;
  private Long aiModelId;
  private Long revision;
  private List<SearchNodeResult> nodes;

  public Long tenantId() {
    return tenantId;
  }

  public Long aiModelId() {
    return aiModelId;
  }

  public Long revision() {
    return revision;
  }

  public List<SearchNodeResult> nodes() {
    return nodes;
  }
}
