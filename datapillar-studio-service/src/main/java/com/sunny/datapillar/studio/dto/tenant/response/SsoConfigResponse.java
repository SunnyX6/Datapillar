package com.sunny.datapillar.studio.dto.tenant.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "SsoConfigResponse")
public class SsoConfigResponse {

    private Long id;

    private Long tenantId;

    private String provider;

    private String baseUrl;

    private Integer status;

    private Boolean hasClientSecret;

    private SsoDingtalkConfigItem config;

    private LocalDateTime updatedAt;
}
