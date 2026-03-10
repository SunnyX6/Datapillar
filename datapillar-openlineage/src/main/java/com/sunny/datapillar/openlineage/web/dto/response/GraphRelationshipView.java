package com.sunny.datapillar.openlineage.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Graph relationship view model for frontend. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GraphRelationshipView {

  private String id;
  private String type;
  private String start;
  private String end;

  public String id() {
    return id;
  }

  public String type() {
    return type;
  }

  public String start() {
    return start;
  }

  public String end() {
    return end;
  }
}
