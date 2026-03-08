package com.sunny.datapillar.studio.integration.gravitino.model.request;

import java.util.List;
import lombok.Data;

@Data
public class ValueDomainUpdateCommand {

  private String domainName;

  private String domainLevel;

  private List<ValueDomainItemCommand> items;

  private String comment;

  private String dataType;
}
