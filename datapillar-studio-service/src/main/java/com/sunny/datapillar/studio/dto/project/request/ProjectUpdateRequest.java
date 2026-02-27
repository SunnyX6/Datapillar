package com.sunny.datapillar.studio.dto.project.request;

import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ProjectUpdate")
public class ProjectUpdateRequest {

    @Size(max = 100, message = "项目名称长度不能超过100个字符")
    private String name;

    @Size(max = 500, message = "项目描述长度不能超过500个字符")
    private String description;

    private ProjectStatus status;

    private List<String> tags;

    private Boolean isFavorite;

    private Boolean isVisible;
}
