package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoMetadataClient;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSemanticClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ObjectTagAlterCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TagCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TagUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoTagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoTagServiceImpl implements GravitinoTagService {

  private final GravitinoMetadataClient metadataClient;
  private final GravitinoSemanticClient semanticClient;
  private final GravitinoClientFactory clientFactory;
  private final GravitinoDomainRoutingService domainRoutingService;
  private final ObjectMapper objectMapper;

  @Override
  public List<GravitinoTagSummaryResponse> listTags() {
    return metadataClient.listTags(clientFactory.requiredMetalake());
  }

  @Override
  public GravitinoTagResponse createTag(TagCreateCommand command) {
    return metadataClient.createTag(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoTagResponse loadTag(String tagName) {
    return metadataClient.loadTag(clientFactory.requiredMetalake(), tagName);
  }

  @Override
  public GravitinoTagResponse updateTag(String tagName, TagUpdateCommand command) {
    return metadataClient.updateTag(
        clientFactory.requiredMetalake(), tagName, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteTag(String tagName) {
    return metadataClient.deleteTag(clientFactory.requiredMetalake(), tagName);
  }

  @Override
  public List<GravitinoTagSummaryResponse> listObjectTags(
      String domain, String objectType, String fullName) {
    return useSemantic(domain)
        ? semanticClient.listObjectTags(
            domainRoutingService.resolveMetalake(domain), objectType, fullName)
        : metadataClient.listObjectTags(
            domainRoutingService.resolveMetalake(domain), objectType, fullName);
  }

  @Override
  public List<GravitinoTagSummaryResponse> alterObjectTags(
      String domain, String objectType, String fullName, ObjectTagAlterCommand command) {
    return useSemantic(domain)
        ? semanticClient.alterObjectTags(
            domainRoutingService.resolveMetalake(domain),
            objectType,
            fullName,
            objectMapper.valueToTree(command))
        : metadataClient.alterObjectTags(
            domainRoutingService.resolveMetalake(domain),
            objectType,
            fullName,
            objectMapper.valueToTree(command));
  }

  private boolean useSemantic(String domain) {
    return GravitinoDomainRoutingService.DOMAIN_SEMANTIC.equals(
        domainRoutingService.normalizeDomain(domain));
  }
}
