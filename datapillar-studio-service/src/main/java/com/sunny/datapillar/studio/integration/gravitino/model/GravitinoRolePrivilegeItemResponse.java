package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoRolePrivilegeItemResponse {

  private String metalake;

  private String objectType;

  private String objectName;

  private String columnName;

  private String privilegeCode;
}
