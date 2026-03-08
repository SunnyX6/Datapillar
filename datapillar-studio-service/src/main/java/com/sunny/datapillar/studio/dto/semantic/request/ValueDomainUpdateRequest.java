package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ValueDomainUpdateRequest")
public class ValueDomainUpdateRequest {

  private String domainName;

  private String domainLevel;

  private List<ValueDomainItemRequest> items;

  private String comment;

  private String dataType;
}
