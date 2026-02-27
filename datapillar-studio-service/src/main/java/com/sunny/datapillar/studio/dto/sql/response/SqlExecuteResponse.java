package com.sunny.datapillar.studio.dto.sql.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "SqlExecuteResponse")
public class SqlExecuteResponse {

    private boolean success;

    private String error;

    private List<SqlColumnSchemaItem> columns;

    private List<List<Object>> rows;

    private int rowCount;

    private boolean hasMore;

    private long executionTime;

    private String message;
}
