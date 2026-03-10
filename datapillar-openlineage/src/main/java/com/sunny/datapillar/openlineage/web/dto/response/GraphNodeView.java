package com.sunny.datapillar.openlineage.web.dto.response;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Graph node view model for frontend. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeView {

  private String id;
  private String type;
  private Integer level;
  private Map<String, Object> properties;

  public String id() {
    return id;
  }

  public String type() {
    return type;
  }

  public Integer level() {
    return level;
  }

  public Map<String, Object> properties() {
    return properties;
  }
}
