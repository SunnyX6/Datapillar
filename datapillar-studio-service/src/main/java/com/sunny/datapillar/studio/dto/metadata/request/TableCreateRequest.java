package com.sunny.datapillar.studio.dto.metadata.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.apache.gravitino.dto.rel.ColumnDTO;
import org.apache.gravitino.dto.rel.DistributionDTO;
import org.apache.gravitino.dto.rel.SortOrderDTO;
import org.apache.gravitino.dto.rel.indexes.IndexDTO;
import org.apache.gravitino.dto.rel.partitioning.Partitioning;

@Data
@Schema(name = "TableCreateRequest")
public class TableCreateRequest {

  @NotBlank(message = "Table name cannot be empty")
  private String name;

  private String comment;

  @NotEmpty(message = "Table columns cannot be empty")
  private List<ColumnDTO> columns;

  private Map<String, String> properties;

  private List<SortOrderDTO> sortOrders;

  private DistributionDTO distribution;

  private List<Partitioning> partitioning;

  private List<IndexDTO> indexes;
}
