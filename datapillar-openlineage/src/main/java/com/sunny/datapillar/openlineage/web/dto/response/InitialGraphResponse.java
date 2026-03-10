package com.sunny.datapillar.openlineage.web.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Initial graph response for visualization. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InitialGraphResponse {

  private Long tenantId;
  private List<GraphNodeView> nodes;
  private List<GraphRelationshipView> relationships;

  public Long tenantId() {
    return tenantId;
  }

  public List<GraphNodeView> nodes() {
    return nodes;
  }

  public List<GraphRelationshipView> relationships() {
    return relationships;
  }
}
