package com.sunny.datapillar.studio.module.governance.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunny.datapillar.connector.gravitino.GravitinoConnector;
import com.sunny.datapillar.connector.runtime.ConnectorKernel;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.studio.module.governance.service.GovernanceMetadataService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Governance metadata service implementation. */
@Service
@RequiredArgsConstructor
public class GovernanceMetadataServiceImpl implements GovernanceMetadataService {

  private final ConnectorKernel connectorKernel;
  private final ObjectMapper objectMapper;

  @Override
  public JsonNode proxy(String method, String path, Map<String, String> query, JsonNode body) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("method", normalizeMethod(method));
    payload.put("path", normalizePath(path));
    payload.set("query", objectMapper.valueToTree(query == null ? Map.of() : query));
    payload.set("body", body == null || body.isNull() ? objectMapper.createObjectNode() : body);

    return connectorKernel
        .invoke(
            ConnectorInvocation.builder(
                    GravitinoConnector.CONNECTOR_ID, GravitinoConnector.OP_METADATA_PROXY)
                .payload(payload)
                .build())
        .payload();
  }

  private String normalizeMethod(String method) {
    if (method == null || method.isBlank()) {
      return "GET";
    }
    return method.trim().toUpperCase();
  }

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
