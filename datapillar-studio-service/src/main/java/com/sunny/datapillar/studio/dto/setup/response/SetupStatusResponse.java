package com.sunny.datapillar.studio.dto.setup.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "SetupStatusResponse")
public class SetupStatusResponse {

    private boolean schemaReady;

    private boolean initialized;

    private String currentStep;

    private List<SetupStepStatusItem> steps;
}
