package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "RoleDataPrivilegeCommand")
public class RoleDataPrivilegeCommandItem {

  private String objectType;

  private String objectName;

  private List<String> columnNames;

  private List<String> privilegeCodes;
}
