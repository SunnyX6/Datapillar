package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "UserStatusUpdate")
public class UserStatusRequest {

    private Integer status;
}
