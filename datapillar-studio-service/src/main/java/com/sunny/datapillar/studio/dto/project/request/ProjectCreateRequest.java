package com.sunny.datapillar.studio.dto.project.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ProjectCreate")
public class ProjectCreateRequest {

  @NotBlank(message = "Project name cannot be empty")
  @Size(max = 100, message = "The project name cannot be longer than100characters")
  private String name;

  @Size(max = 500, message = "Item description length cannot exceed500characters")
  private String description;

  private List<String> tags;

  private Boolean isVisible = true;
}
