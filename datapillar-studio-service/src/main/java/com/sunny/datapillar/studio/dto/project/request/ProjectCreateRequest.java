package com.sunny.datapillar.studio.dto.project.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ProjectCreate")
public class ProjectCreateRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100个字符")
    private String name;

    @Size(max = 500, message = "项目描述长度不能超过500个字符")
    private String description;

    private List<String> tags;

    private Boolean isVisible = true;
}
