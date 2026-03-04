package com.sunny.datapillar.connector.gravitino.transport.sdk;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.connector.gravitino.config.GravitinoConnectorConfig;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.gravitino.client.GravitinoAdminClient;
import org.apache.gravitino.client.GravitinoClient;

/** Factory for Gravitino Java clients. */
public class GravitinoSdkClientFactory {

  private final GravitinoConnectorConfig config;

  public GravitinoSdkClientFactory(GravitinoConnectorConfig config) {
    this.config = config;
  }

  public GravitinoAdminClient createAdminClient(ConnectorContext context) {
    return GravitinoAdminClient.builder(config.normalizedEndpoint())
        .withSimpleAuth(config.simpleAuthUser())
        .withVersionCheckDisabled()
        .withHeaders(buildHeaders(context))
        .build();
  }

  public GravitinoClient createMetadataClient(ConnectorContext context) {
    return GravitinoClient.builder(config.normalizedEndpoint())
        .withMetalake(config.metadataMetalake())
        .withSimpleAuth(config.simpleAuthUser())
        .withVersionCheckDisabled()
        .withHeaders(buildHeaders(context))
        .build();
  }

  public GravitinoClient createSemanticClient(ConnectorContext context) {
    return GravitinoClient.builder(config.normalizedEndpoint())
        .withMetalake(config.semanticMetalake())
        .withSimpleAuth(config.simpleAuthUser())
        .withVersionCheckDisabled()
        .withHeaders(buildHeaders(context))
        .build();
  }

  public Map<String, String> buildHeaders(ConnectorContext context) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (context == null) {
      return headers;
    }
    putIfPresent(headers, HeaderConstants.HEADER_TENANT_ID, toStringValue(context.tenantId()));
    putIfPresent(headers, HeaderConstants.HEADER_TENANT_CODE, context.tenantCode());
    putIfPresent(headers, HeaderConstants.HEADER_USER_ID, toStringValue(context.userId()));
    putIfPresent(headers, HeaderConstants.HEADER_USERNAME, context.username());
    putIfPresent(headers, HeaderConstants.HEADER_PRINCIPAL_SUB, context.principalSub());
    putIfPresent(
        headers, HeaderConstants.HEADER_ACTOR_USER_ID, toStringValue(context.actorUserId()));
    putIfPresent(
        headers, HeaderConstants.HEADER_ACTOR_TENANT_ID, toStringValue(context.actorTenantId()));
    putIfPresent(
        headers, HeaderConstants.HEADER_IMPERSONATION, String.valueOf(context.impersonation()));
    putIfPresent(headers, HeaderConstants.HEADER_TRACE_ID, context.traceId());
    putIfPresent(headers, HeaderConstants.HEADER_REQUEST_ID, context.requestId());
    return headers;
  }

  public GravitinoConnectorConfig config() {
    return config;
  }

  private String toStringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private void putIfPresent(Map<String, String> target, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    target.put(key, value);
  }
}
