package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.List;
import lombok.Data;

@Data
public class GravitinoUserResponse {

  private String metalake;

  private String name;

  private List<String> roles;

  private GravitinoAuditResponse audit;
}
