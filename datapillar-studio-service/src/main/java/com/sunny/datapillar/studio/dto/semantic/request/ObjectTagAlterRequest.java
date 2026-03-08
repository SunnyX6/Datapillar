package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "SemanticObjectTagAlterRequest")
public class ObjectTagAlterRequest {

  private List<String> tagsToAdd;

  private List<String> tagsToRemove;
}
