package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSemanticClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.WordRootCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.WordRootUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoWordRootService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoWordRootServiceImpl implements GravitinoWordRootService {

  private final GravitinoSemanticClient semanticClient;
  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  @Override
  public GravitinoPageResult<GravitinoWordRootSummaryResponse> listWordRoots(
      int offset, int limit) {
    return semanticClient.listWordRoots(clientFactory.requiredMetalake(), offset, limit);
  }

  @Override
  public GravitinoWordRootResponse createWordRoot(WordRootCreateCommand command) {
    return semanticClient.createWordRoot(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoWordRootResponse loadWordRoot(String code) {
    return semanticClient.loadWordRoot(clientFactory.requiredMetalake(), code);
  }

  @Override
  public GravitinoWordRootResponse updateWordRoot(String code, WordRootUpdateCommand command) {
    return semanticClient.updateWordRoot(
        clientFactory.requiredMetalake(), code, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteWordRoot(String code) {
    return semanticClient.deleteWordRoot(clientFactory.requiredMetalake(), code);
  }
}
