package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogTestConnectionCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogUpdateCommand;
import java.util.List;

public interface GravitinoCatalogService {

  List<GravitinoCatalogSummaryResponse> listCatalogs();

  void testCatalogConnection(CatalogTestConnectionCommand command);

  GravitinoCatalogResponse createCatalog(CatalogCreateCommand command);

  GravitinoCatalogResponse loadCatalog(String catalogName);

  GravitinoCatalogResponse updateCatalog(String catalogName, CatalogUpdateCommand command);

  boolean deleteCatalog(String catalogName, boolean force);

  boolean createCatalogIfAbsent(
      String metalake, CatalogCreateCommand command, String principalUsername);

  boolean deleteCatalog(
      String metalake, String catalogName, boolean force, String principalUsername);

  void setCatalogOwner(
      String metalake, String catalogName, String ownerName, String principalUsername);
}
