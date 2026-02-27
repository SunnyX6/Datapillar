package com.sunny.datapillar.studio.dto.workflow.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "JobLayoutSave")
public class JobLayoutSaveRequest {

    @NotEmpty(message = "位置列表不能为空")
    @Valid
    private List<JobPositionItem> positions;
}
