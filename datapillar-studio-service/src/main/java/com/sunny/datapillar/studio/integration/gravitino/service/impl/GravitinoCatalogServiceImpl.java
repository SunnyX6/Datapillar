package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoMetadataClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogTestConnectionCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogUpdateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoCatalogServiceImpl implements GravitinoCatalogService {

  private final GravitinoClientFactory clientFactory;
  private final GravitinoMetadataClient metadataClient;
  private final ObjectMapper objectMapper;

  @Override
  public List<GravitinoCatalogSummaryResponse> listCatalogs() {
    return metadataClient.listCatalogs(clientFactory.requiredMetalake());
  }

  @Override
  public void testCatalogConnection(CatalogTestConnectionCommand command) {
    metadataClient.testCatalogConnection(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoCatalogResponse createCatalog(CatalogCreateCommand command) {
    return metadataClient.createCatalog(
        clientFactory.requiredMetalake(), objectMapper.valueToTree(command));
  }

  @Override
  public GravitinoCatalogResponse loadCatalog(String catalogName) {
    return metadataClient.loadCatalog(clientFactory.requiredMetalake(), catalogName);
  }

  @Override
  public GravitinoCatalogResponse updateCatalog(String catalogName, CatalogUpdateCommand command) {
    return metadataClient.updateCatalog(
        clientFactory.requiredMetalake(), catalogName, objectMapper.valueToTree(command));
  }

  @Override
  public boolean deleteCatalog(String catalogName, boolean force) {
    return metadataClient.deleteCatalog(clientFactory.requiredMetalake(), catalogName, force);
  }

  @Override
  public boolean createCatalogIfAbsent(
      String metalake, CatalogCreateCommand command, String principalUsername) {
    return metadataClient.createSemanticCatalog(
        metalake, objectMapper.valueToTree(command), principalUsername);
  }

  @Override
  public boolean deleteCatalog(
      String metalake, String catalogName, boolean force, String principalUsername) {
    return metadataClient.deleteCatalog(metalake, catalogName, force, principalUsername);
  }

  @Override
  public void setCatalogOwner(
      String metalake, String catalogName, String ownerName, String principalUsername) {
    metadataClient.setCatalogOwner(metalake, catalogName, ownerName, principalUsername);
  }
}
