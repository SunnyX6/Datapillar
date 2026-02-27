package com.sunny.datapillar.studio.dto.sql.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SqlColumnSchemaItem")
public class SqlColumnSchemaItem {

    private String name;

    private String type;

    private boolean nullable;

    public SqlColumnSchemaItem() {
    }

    public SqlColumnSchemaItem(String name, String type, boolean nullable) {
        this.name = name;
        this.type = type;
        this.nullable = nullable;
    }
}
