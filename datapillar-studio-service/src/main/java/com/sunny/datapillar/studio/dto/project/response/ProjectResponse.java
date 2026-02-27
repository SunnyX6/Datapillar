package com.sunny.datapillar.studio.dto.project.response;

import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "ProjectResponse")
public class ProjectResponse {

    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private String ownerName;

    private ProjectStatus status;

    private List<String> tags;

    private Boolean isFavorite;

    private Boolean isVisible;

    private Integer memberCount;

    private LocalDateTime lastAccessedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
