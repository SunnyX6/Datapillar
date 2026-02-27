package com.sunny.datapillar.studio.dto.setup.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SetupStepStatusItem")
public class SetupStepStatusItem {

    private String code;

    private String name;

    private String description;

    private String status;
}
