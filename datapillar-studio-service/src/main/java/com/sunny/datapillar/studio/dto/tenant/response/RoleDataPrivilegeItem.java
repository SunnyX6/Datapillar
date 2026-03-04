package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "RoleDataPrivilege")
public class RoleDataPrivilegeItem {

  private String domain;

  private String objectType;

  private String objectName;

  private String columnName;

  private String privilegeCode;
}
