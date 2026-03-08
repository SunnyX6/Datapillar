package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "UserRoleReplaceRequest")
public class UserRoleReplaceRequest {

  private List<Long> roleIds;
}
