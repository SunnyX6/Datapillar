package com.sunny.datapillar.studio.dto.user.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "RoleResponse")
public class RoleResponse {

  private Long id;

  @JsonSerialize(using = ToStringSerializer.class)
  private Long tenantId;

  private String type;

  private String name;

  private String description;

  private Integer level;

  private Integer status;

  private Integer sort;

  private Long memberCount;
}
