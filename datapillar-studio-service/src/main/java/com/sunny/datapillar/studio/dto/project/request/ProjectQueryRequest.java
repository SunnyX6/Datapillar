package com.sunny.datapillar.studio.dto.project.request;

import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "ProjectQuery")
public class ProjectQueryRequest {

    private String keyword;

    private ProjectStatus status;

    private Boolean onlyFavorites;

    private Boolean onlyVisible;

    private Integer limit = 20;

    private Integer offset = 0;

    private Integer maxLimit = 200;

    private String sortBy = "updatedAt";

    private String sortOrder = "desc";
}
