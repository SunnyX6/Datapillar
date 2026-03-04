package com.sunny.datapillar.studio.dto.sql.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "SqlExecuteRequest")
public class SqlExecuteRequest {

  @NotBlank(message = "SQL Statement cannot be empty")
  private String sql;

  private String catalog;

  private String database;

  private Integer maxRows;
}
