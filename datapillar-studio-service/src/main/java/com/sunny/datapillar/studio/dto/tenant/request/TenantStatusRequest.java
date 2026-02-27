package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "TenantStatusUpdate")
public class TenantStatusRequest {

    private Integer status;
}
