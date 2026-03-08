package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSemanticClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ValueDomainCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ValueDomainUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoValueDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoValueDomainServiceImpl implements GravitinoValueDomainService {

  private final GravitinoSemanticClient semanticClient;
  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  @Override
  public GravitinoPageResult<GravitinoValueDomainSummaryResponse> listValueDomains(
      int offset, int limit) {
    return semanticClient.listValueDomains(clientFactory.requiredMetalake(), offset, limit);
  }

  @Override
  public GravitinoValueDomainResponse createValueDomain(ValueDomainCreateCommand command) {
    return semanticClient.createValueDomain(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoValueDomainResponse loadValueDomain(String code) {
    return semanticClient.loadValueDomain(clientFactory.requiredMetalake(), code);
  }

  @Override
  public GravitinoValueDomainResponse updateValueDomain(
      String code, ValueDomainUpdateCommand command) {
    return semanticClient.updateValueDomain(
        clientFactory.requiredMetalake(), code, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteValueDomain(String code) {
    return semanticClient.deleteValueDomain(clientFactory.requiredMetalake(), code);
  }
}
