package com.sunny.datapillar.studio.dto.user.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "RoleResponse")
public class RoleResponse {

    private Long id;

    private Long tenantId;

    private String type;

    private String name;

    private String description;

    private Integer level;

    private Integer status;

    private Integer sort;

    private Long memberCount;
}
