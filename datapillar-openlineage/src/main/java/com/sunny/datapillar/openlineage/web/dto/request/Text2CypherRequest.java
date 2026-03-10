package com.sunny.datapillar.openlineage.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** text2cypher request. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Text2CypherRequest {

  @NotBlank(message = "query cannot be empty")
  private String query;

  private Integer limit;

  public String query() {
    return query;
  }

  public Integer limit() {
    return limit;
  }
}
