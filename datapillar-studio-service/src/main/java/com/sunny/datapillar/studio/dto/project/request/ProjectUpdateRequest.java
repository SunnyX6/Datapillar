package com.sunny.datapillar.studio.dto.project.request;

import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ProjectUpdate")
public class ProjectUpdateRequest {

  @Size(max = 100, message = "The project name cannot be longer than100characters")
  private String name;

  @Size(max = 500, message = "Item description length cannot exceed500characters")
  private String description;

  private ProjectStatus status;

  private List<String> tags;

  private Boolean isFavorite;

  private Boolean isVisible;
}
