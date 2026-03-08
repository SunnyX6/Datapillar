package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPrivilegeCommandRequest;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRolePrivilegeItemResponse;
import java.util.List;

public final class GravitinoDataPrivilegeMapper {

  private GravitinoDataPrivilegeMapper() {}

  public static List<GravitinoPrivilegeCommandRequest> toGravitinoCommands(
      List<RoleDataPrivilegeCommandItem> commands) {
    if (commands == null || commands.isEmpty()) {
      return List.of();
    }
    return commands.stream().map(GravitinoDataPrivilegeMapper::toGravitinoCommand).toList();
  }

  public static RoleDataPrivilegeItem toRoleDataPrivilegeItem(
      String domain, GravitinoRolePrivilegeItemResponse source) {
    RoleDataPrivilegeItem item = new RoleDataPrivilegeItem();
    item.setDomain(domain);
    item.setObjectType(source.getObjectType());
    item.setObjectName(source.getObjectName());
    item.setColumnName(source.getColumnName());
    item.setPrivilegeCode(source.getPrivilegeCode());
    return item;
  }

  private static GravitinoPrivilegeCommandRequest toGravitinoCommand(
      RoleDataPrivilegeCommandItem source) {
    GravitinoPrivilegeCommandRequest request = new GravitinoPrivilegeCommandRequest();
    if (source == null) {
      return request;
    }
    request.setObjectType(source.getObjectType());
    request.setObjectName(source.getObjectName());
    request.setColumnNames(source.getColumnNames());
    request.setPrivilegeCodes(source.getPrivilegeCodes());
    return request;
  }
}
