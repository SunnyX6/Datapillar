package com.sunny.datapillar.studio.dto.metadata.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "TableUpdateRequest")
public class TableUpdateRequest {

  private List<TableUpdateOperationRequest> updates;
}
