package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSemanticClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ModifierCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ModifierUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoModifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoModifierServiceImpl implements GravitinoModifierService {

  private final GravitinoSemanticClient semanticClient;
  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  @Override
  public GravitinoPageResult<GravitinoModifierSummaryResponse> listModifiers(
      int offset, int limit) {
    return semanticClient.listModifiers(clientFactory.requiredMetalake(), offset, limit);
  }

  @Override
  public GravitinoModifierResponse createModifier(ModifierCreateCommand command) {
    return semanticClient.createModifier(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoModifierResponse loadModifier(String code) {
    return semanticClient.loadModifier(clientFactory.requiredMetalake(), code);
  }

  @Override
  public GravitinoModifierResponse updateModifier(String code, ModifierUpdateCommand command) {
    return semanticClient.updateModifier(
        clientFactory.requiredMetalake(), code, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteModifier(String code) {
    return semanticClient.deleteModifier(clientFactory.requiredMetalake(), code);
  }
}
