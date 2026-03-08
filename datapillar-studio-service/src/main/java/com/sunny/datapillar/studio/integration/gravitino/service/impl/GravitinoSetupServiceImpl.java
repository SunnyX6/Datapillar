package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSystemConstants;
import com.sunny.datapillar.studio.integration.gravitino.model.request.CatalogCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.SchemaCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoCatalogService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetalakeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRolePrivilegeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRoleService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSchemaService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSetupService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GravitinoSetupServiceImpl implements GravitinoSetupService {

  private final GravitinoClientFactory gravitinoClientFactory;
  private final GravitinoMetalakeService gravitinoMetalakeService;
  private final GravitinoCatalogService gravitinoCatalogService;
  private final GravitinoSchemaService gravitinoSchemaService;
  private final GravitinoRolePrivilegeService gravitinoRolePrivilegeService;
  private final GravitinoRoleService gravitinoRoleService;
  private final GravitinoUserService gravitinoUserService;
  private final ObjectMapper objectMapper;

  @Override
  public void initializeResources(
      Long tenantId,
      String tenantCode,
      Long adminUserId,
      String adminUsername,
      String adminRoleName) {
    TenantContext previousContext = TenantContextHolder.get();
    TenantContext setupContext = new TenantContext(tenantId, tenantCode, null, null, false);
    TenantContextHolder.set(setupContext);
    String principalUsername = gravitinoClientFactory.resolveSetupPrincipalUsername();
    try {
      gravitinoMetalakeService.createMetalake(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
          GravitinoSystemConstants.MANAGED_METALAKE_COMMENT,
          objectMapper.createObjectNode(),
          principalUsername);
      gravitinoUserService.createUser(adminUsername, adminUserId, principalUsername);
      gravitinoRoleService.createRole(adminRoleName, principalUsername);

      CatalogCreateCommand semanticCatalogCommand = new CatalogCreateCommand();
      semanticCatalogCommand.setName(GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS);
      semanticCatalogCommand.setType("DATASET");
      semanticCatalogCommand.setProvider("dataset");
      semanticCatalogCommand.setComment(GravitinoSystemConstants.SEMANTIC_CATALOG_COMMENT);
      semanticCatalogCommand.setProperties(java.util.Map.of());
      gravitinoCatalogService.createCatalogIfAbsent(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
          semanticCatalogCommand,
          principalUsername);

      SchemaCreateCommand semanticSchemaCommand = new SchemaCreateCommand();
      semanticSchemaCommand.setName(GravitinoSystemConstants.SEMANTIC_SCHEMA_ONE_DS);
      semanticSchemaCommand.setComment(GravitinoSystemConstants.SEMANTIC_SCHEMA_COMMENT);
      semanticSchemaCommand.setProperties(java.util.Map.of());
      gravitinoSchemaService.createSchemaIfAbsent(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
          GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS,
          semanticSchemaCommand,
          principalUsername);

      gravitinoRolePrivilegeService.replaceRoleDataPrivileges(
          adminRoleName,
          GravitinoDomainRoutingService.DOMAIN_METADATA,
          List.of(),
          principalUsername);
      gravitinoRolePrivilegeService.replaceRoleDataPrivileges(
          adminRoleName,
          GravitinoDomainRoutingService.DOMAIN_SEMANTIC,
          List.of(),
          principalUsername);

      gravitinoSchemaService.setSchemaOwner(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
          GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS,
          GravitinoSystemConstants.SEMANTIC_SCHEMA_ONE_DS,
          adminUsername,
          principalUsername);
      gravitinoCatalogService.setCatalogOwner(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META,
          GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS,
          adminUsername,
          principalUsername);
      gravitinoMetalakeService.setMetalakeOwner(
          GravitinoSystemConstants.MANAGED_METALAKE_ONE_META, adminUsername, principalUsername);
    } finally {
      if (previousContext == null) {
        TenantContextHolder.clear();
      } else {
        TenantContextHolder.set(previousContext);
      }
    }
  }
}
