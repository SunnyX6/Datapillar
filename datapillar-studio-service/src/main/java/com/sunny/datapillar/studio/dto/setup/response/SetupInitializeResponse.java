package com.sunny.datapillar.studio.dto.setup.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SetupInitializeResponse")
public class SetupInitializeResponse {

  @JsonSerialize(using = ToStringSerializer.class)
  private Long tenantId;

  private Long userId;
}
