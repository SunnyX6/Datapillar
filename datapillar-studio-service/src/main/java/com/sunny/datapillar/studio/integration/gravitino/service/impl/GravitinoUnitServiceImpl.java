package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSemanticClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.UnitCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.UnitUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoUnitServiceImpl implements GravitinoUnitService {

  private final GravitinoSemanticClient semanticClient;
  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  @Override
  public GravitinoPageResult<GravitinoUnitSummaryResponse> listUnits(int offset, int limit) {
    return semanticClient.listUnits(clientFactory.requiredMetalake(), offset, limit);
  }

  @Override
  public GravitinoUnitResponse createUnit(UnitCreateCommand command) {
    return semanticClient.createUnit(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoUnitResponse loadUnit(String code) {
    return semanticClient.loadUnit(clientFactory.requiredMetalake(), code);
  }

  @Override
  public GravitinoUnitResponse updateUnit(String code, UnitUpdateCommand command) {
    return semanticClient.updateUnit(
        clientFactory.requiredMetalake(), code, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteUnit(String code) {
    return semanticClient.deleteUnit(clientFactory.requiredMetalake(), code);
  }
}
