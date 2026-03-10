package com.sunny.datapillar.openlineage.source;

import com.fasterxml.jackson.databind.JsonNode;

/** Source adapter for parsing MQ OpenLineage payload into current graph models. */
public interface OpenLineageSource {

  boolean supports(JsonNode payload);

  OpenLineageSourceModels readModels(Long tenantId, JsonNode payload);
}
