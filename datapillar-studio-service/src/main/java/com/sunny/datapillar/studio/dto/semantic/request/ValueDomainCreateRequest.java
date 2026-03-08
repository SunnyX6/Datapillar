package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ValueDomainCreateRequest")
public class ValueDomainCreateRequest {

  @NotBlank(message = "Value domain code cannot be empty")
  private String domainCode;

  @NotBlank(message = "Value domain name cannot be empty")
  private String domainName;

  @NotBlank(message = "Value domain type cannot be empty")
  private String domainType;

  private String domainLevel;

  private List<ValueDomainItemRequest> items;

  private String comment;

  private String dataType;
}
