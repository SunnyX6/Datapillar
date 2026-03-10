package com.sunny.datapillar.openlineage.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Gravitino OpenLineage event source reader. */
@Component
public class GravitinoOpenlineageSource extends AbstractOpenLineageSource {

  public GravitinoOpenlineageSource(
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    super(openLineageObjectMapper);
  }

  @Override
  public boolean supports(JsonNode payload) {
    return matchesGravitino(payload);
  }
}
