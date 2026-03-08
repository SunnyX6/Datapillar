package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class ValueDomainCreateCommand {

  @NotBlank(message = "Value domain code cannot be empty")
  private String domainCode;

  @NotBlank(message = "Value domain name cannot be empty")
  private String domainName;

  @NotBlank(message = "Value domain type cannot be empty")
  private String domainType;

  private String domainLevel;

  private List<ValueDomainItemCommand> items;

  private String comment;

  private String dataType;
}
