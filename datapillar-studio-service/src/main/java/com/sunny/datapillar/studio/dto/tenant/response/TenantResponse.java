package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "TenantResponse")
public class TenantResponse {

    private Long id;

    private String code;

    private String name;

    private String type;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
