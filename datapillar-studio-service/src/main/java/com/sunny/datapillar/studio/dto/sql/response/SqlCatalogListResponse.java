package com.sunny.datapillar.studio.dto.sql.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "SqlCatalogListResponse")
public class SqlCatalogListResponse {

    private List<String> catalogs;

    private String currentCatalog;
}
