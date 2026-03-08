package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "ValueDomainItemRequest")
public class ValueDomainItemRequest {

  private String value;

  private String label;
}
