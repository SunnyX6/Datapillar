package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "RoleMemberBatchRemove")
public class RoleMemberBatchRemoveRequest {

  @NotEmpty(message = "memberIDList cannot be empty")
  private List<@NotNull(message = "memberIDcannot be empty") Long> userIds;
}
