package com.sunny.datapillar.studio.dto.metadata.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "TagUpdateRequest")
public class TagUpdateRequest {

  private String comment;

  private Map<String, String> properties;

  private List<TagUpdateOperationRequest> updates;
}
