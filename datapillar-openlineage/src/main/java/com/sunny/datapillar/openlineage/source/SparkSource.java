package com.sunny.datapillar.openlineage.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spark OpenLineage event source reader. */
@Component
public class SparkSource extends AbstractOpenLineageSource {

  public SparkSource(@Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    super(openLineageObjectMapper);
  }

  @Override
  public boolean supports(JsonNode payload) {
    return matchesEngine(payload, "spark");
  }
}
