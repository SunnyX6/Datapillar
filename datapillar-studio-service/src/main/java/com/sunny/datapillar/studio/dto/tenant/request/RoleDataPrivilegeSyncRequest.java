package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "RoleDataPrivilegeSync")
public class RoleDataPrivilegeSyncRequest {

  private String domain;

  private List<RoleDataPrivilegeCommandItem> commands;
}
