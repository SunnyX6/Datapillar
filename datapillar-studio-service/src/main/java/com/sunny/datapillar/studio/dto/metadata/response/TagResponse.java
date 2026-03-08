package com.sunny.datapillar.studio.dto.metadata.response;

import java.util.Map;
import lombok.Data;

@Data
public class TagResponse {

  private String metalake;

  private String name;

  private String comment;

  private Map<String, String> properties;

  private AuditResponse audit;
}
