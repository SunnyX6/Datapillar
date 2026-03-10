package com.sunny.datapillar.openlineage.web.service;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.SearchRequest;
import com.sunny.datapillar.openlineage.web.dto.request.Text2CypherRequest;
import com.sunny.datapillar.openlineage.web.dto.response.InitialGraphResponse;
import com.sunny.datapillar.openlineage.web.dto.response.SearchNodeResult;
import com.sunny.datapillar.openlineage.web.dto.response.SearchResponse;
import com.sunny.datapillar.openlineage.web.dto.response.Text2CypherResponse;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import com.sunny.datapillar.openlineage.web.mapper.KnowledgeGraphMapper;
import com.sunny.datapillar.openlineage.web.security.TenantApiKeyDecryptor;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Query service for initial graph, vector search and text2cypher. */
@Service
public class QueryService {

  private static final String DW_SCOPE = "DW";
  private static final Long DW_OWNER_USER_ID = 0L;

  private final EmbeddingBindingMapper embeddingBindingMapper;
  private final TenantApiKeyDecryptor tenantApiKeyDecryptor;
  private final KnowledgeGraphMapper knowledgeGraphMapper;

  public QueryService(
      EmbeddingBindingMapper embeddingBindingMapper,
      TenantApiKeyDecryptor tenantApiKeyDecryptor,
      KnowledgeGraphMapper knowledgeGraphMapper) {
    this.embeddingBindingMapper = embeddingBindingMapper;
    this.tenantApiKeyDecryptor = tenantApiKeyDecryptor;
    this.knowledgeGraphMapper = knowledgeGraphMapper;
  }

  public SearchResponse search(SearchRequest request) {
    if (request == null) {
      throw new BadRequestException("search payload is missing");
    }
    if (!hasText(request.query())) {
      throw new BadRequestException("query is empty");
    }
    requireIdentity();
    Long tenantId = requireTenantId();
    String tenantCode = requireTenantCode();

    RuntimeModel runtime = requireDwEmbeddingRuntime(tenantId, tenantCode);
    List<Double> queryVector = embed(runtime, request.query());
    List<SearchNodeResult> nodes =
        knowledgeGraphMapper.vectorSearch(
            tenantId, queryVector, request.topK(), request.scoreThreshold());

    return new SearchResponse(tenantId, runtime.aiModelId(), runtime.revision(), nodes);
  }

  public InitialGraphResponse initial(Integer limit) {
    requireIdentity();
    Long tenantId = requireTenantId();
    int safeLimit = limit == null || limit <= 0 ? 500 : Math.min(limit, 5000);
    return knowledgeGraphMapper.loadInitialGraph(tenantId, safeLimit);
  }

  public Text2CypherResponse text2cypher(Text2CypherRequest request) {
    if (request == null) {
      throw new BadRequestException("text2cypher payload is missing");
    }
    if (!hasText(request.query())) {
      throw new BadRequestException("query is empty");
    }
    TrustedIdentityContext identity = requireIdentity();
    return knowledgeGraphMapper.queryByText(identity.tenantId(), request.query(), request.limit());
  }

  private RuntimeModel requireDwEmbeddingRuntime(Long tenantId, String tenantCode) {
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
    if (!hasText(tenantCode)) {
      throw new BadRequestException("tenantCode is invalid");
    }

    List<EmbeddingBindingMapper.RuntimeModelRow> rows =
        embeddingBindingMapper.selectDwRuntimeByTenant(tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    if (rows == null || rows.isEmpty()) {
      throw new BadRequestException("DW embedding model is not configured");
    }
    if (rows.size() > 1) {
      throw new BadRequestException(
          "DW embedding binding duplicated: tenantId=%s scope=%s ownerUserId=%s",
          tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    }

    EmbeddingBindingMapper.RuntimeModelRow row = rows.getFirst();
    validateRuntime(row);
    String apiKeyPlaintext =
        tenantApiKeyDecryptor.decryptModelApiKey(tenantCode.trim(), row.getApiKey());
    String baseUrl = hasText(row.getBaseUrl()) ? row.getBaseUrl().trim() : null;
    return new RuntimeModel(
        row.getRevision(),
        row.getAiModelId(),
        row.getProviderCode(),
        row.getProviderModelId(),
        row.getEmbeddingDimension(),
        baseUrl,
        apiKeyPlaintext);
  }

  private void validateRuntime(EmbeddingBindingMapper.RuntimeModelRow row) {
    if (row == null) {
      throw new BadRequestException("model runtime row is empty");
    }
    if (!"embeddings".equalsIgnoreCase(row.getModelType())) {
      throw new BadRequestException("Model type must be embeddings");
    }
    if (!"ACTIVE".equalsIgnoreCase(row.getStatus())) {
      throw new BadRequestException("Model is not active");
    }
    if (!hasText(row.getProviderModelId())) {
      throw new BadRequestException("Model providerModelId is empty");
    }
    if (!hasText(row.getApiKey())) {
      throw new BadRequestException("Model apiKey is empty");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Long requireTenantId() {
    Long tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId <= 0) {
      throw new UnauthorizedException("trusted_identity_tenant_id_missing");
    }
    return tenantId;
  }

  private String requireTenantCode() {
    String tenantCode = TenantContextHolder.getTenantCode();
    if (tenantCode == null || tenantCode.isBlank()) {
      throw new UnauthorizedException("trusted_identity_tenant_code_missing");
    }
    return tenantCode.trim();
  }

  private TrustedIdentityContext requireIdentity() {
    TrustedIdentityContext identity = TrustedIdentityContextHolder.get();
    if (identity == null || identity.principalType() == null || identity.tenantId() == null) {
      throw new UnauthorizedException("trusted_identity_context_missing");
    }
    return identity;
  }

  private List<Double> embed(RuntimeModel runtime, String content) {
    if (runtime == null) {
      throw new InternalException("Model runtime is missing");
    }
    if (!StringUtils.hasText(content)) {
      throw new InternalException("Embedding content is empty");
    }
    try {
      EmbeddingModel model = buildOpenAiEmbeddingModel(runtime);
      Embedding embedding = model.embed(content).content();
      return embedding.vectorAsList().stream().map(Float::doubleValue).toList();
    } catch (RuntimeException ex) {
      throw new InternalException(ex, "Embedding invocation failed");
    }
  }

  private EmbeddingModel buildOpenAiEmbeddingModel(RuntimeModel runtime) {
    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
        OpenAiEmbeddingModel.builder()
            .apiKey(runtime.apiKeyPlaintext())
            .modelName(runtime.providerModelId())
            .timeout(Duration.ofSeconds(30))
            .maxRetries(2);
    if (runtime.embeddingDimension() != null && runtime.embeddingDimension() > 0) {
      builder.dimensions(runtime.embeddingDimension());
    }
    if (StringUtils.hasText(runtime.baseUrl())) {
      builder.baseUrl(runtime.baseUrl());
    }
    return builder.build();
  }

  private static final class RuntimeModel {

    private final Long revision;
    private final Long aiModelId;
    private final String providerCode;
    private final String providerModelId;
    private final Integer embeddingDimension;
    private final String baseUrl;
    private final String apiKeyPlaintext;

    private RuntimeModel(
        Long revision,
        Long aiModelId,
        String providerCode,
        String providerModelId,
        Integer embeddingDimension,
        String baseUrl,
        String apiKeyPlaintext) {
      this.revision = revision;
      this.aiModelId = aiModelId;
      this.providerCode = providerCode;
      this.providerModelId = providerModelId;
      this.embeddingDimension = embeddingDimension;
      this.baseUrl = baseUrl;
      this.apiKeyPlaintext = apiKeyPlaintext;
    }

    private Long revision() {
      return revision;
    }

    private Long aiModelId() {
      return aiModelId;
    }

    private String providerCode() {
      return providerCode;
    }

    private String providerModelId() {
      return providerModelId;
    }

    private Integer embeddingDimension() {
      return embeddingDimension;
    }

    private String baseUrl() {
      return baseUrl;
    }

    private String apiKeyPlaintext() {
      return apiKeyPlaintext;
    }
  }
}
