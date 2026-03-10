package com.sunny.datapillar.openlineage.web.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** text2cypher response. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Text2CypherResponse {

  private Long tenantId;
  private String query;
  private String cypher;
  private List<Map<String, Object>> rows;

  public Long tenantId() {
    return tenantId;
  }

  public String query() {
    return query;
  }

  public String cypher() {
    return cypher;
  }

  public List<Map<String, Object>> rows() {
    return rows;
  }
}
