package com.sunny.datapillar.studio.integration.gravitino.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "GravitinoPrivilegeCommandRequest")
public class GravitinoPrivilegeCommandRequest {

  private String objectType;

  private String objectName;

  private List<String> columnNames;

  private List<String> privilegeCodes;
}
