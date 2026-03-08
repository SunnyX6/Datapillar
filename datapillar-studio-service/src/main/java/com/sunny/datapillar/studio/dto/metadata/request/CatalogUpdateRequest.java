package com.sunny.datapillar.studio.dto.metadata.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(name = "CatalogUpdateRequest")
public class CatalogUpdateRequest {

  private String comment;

  private Map<String, String> properties;

  private List<CatalogUpdateOperationRequest> updates;
}
