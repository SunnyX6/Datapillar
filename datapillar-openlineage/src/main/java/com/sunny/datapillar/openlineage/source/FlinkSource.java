package com.sunny.datapillar.openlineage.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Flink OpenLineage event source reader. */
@Component
public class FlinkSource extends AbstractOpenLineageSource {

  public FlinkSource(@Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    super(openLineageObjectMapper);
  }

  @Override
  public boolean supports(JsonNode payload) {
    return matchesEngine(payload, "flink");
  }
}
