package com.sunny.datapillar.studio.dto.sql.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "SqlDatabaseListResponse")
public class SqlDatabaseListResponse {

    private List<String> databases;

    private String currentDatabase;
}
