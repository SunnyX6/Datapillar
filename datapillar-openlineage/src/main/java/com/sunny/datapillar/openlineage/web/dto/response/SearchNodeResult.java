package com.sunny.datapillar.openlineage.web.dto.response;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Search result node. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchNodeResult {

  private String id;
  private String type;
  private double score;
  private Map<String, Object> properties;

  public String id() {
    return id;
  }

  public String type() {
    return type;
  }

  public double score() {
    return score;
  }

  public Map<String, Object> properties() {
    return properties;
  }
}
