package com.sunny.datapillar.openlineage.sink;

import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.sink.dao.VectorDao;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Vector sink entrypoint for vector read/write against Neo4j. */
@Component
public class VectorSink {

  private final VectorDao vectorDao;

  public VectorSink(VectorDao vectorDao) {
    this.vectorDao = vectorDao;
  }

  public void writeResult(
      Long tenantId, String resourceId, List<Double> vector, String providerCode, Long revision) {
    vectorDao.writeEmbedding(tenantId, resourceId, vector, providerCode, revision);
  }

  public List<EmbeddingTaskPayload> listTenantTasks(
      Tenant tenant,
      EmbeddingBindingMapper.RuntimeModelRow runtime,
      Long targetRevision,
      EmbeddingTriggerType trigger,
      int limit,
      int offset) {
    validateTenant(tenant);
    validateRuntime(runtime);
    if (targetRevision == null || targetRevision <= 0) {
      throw new InternalException("targetRevision is invalid");
    }

    List<EmbeddingTaskPayload> tasks =
        vectorDao.listTenantEmbeddingTasks(tenant.getTenantId(), limit, offset);
    List<EmbeddingTaskPayload> enriched = new ArrayList<>(tasks.size());
    for (EmbeddingTaskPayload task : tasks) {
      if (task == null
          || !StringUtils.hasText(task.getResourceId())
          || !StringUtils.hasText(task.getContent())) {
        continue;
      }
      task.setTenantId(tenant.getTenantId());
      task.setTenantCode(tenant.getTenantCode());
      task.setTargetRevision(targetRevision);
      task.setTrigger(trigger);
      task.setAiModelId(runtime.getAiModelId());
      task.setProviderCode(runtime.getProviderCode());
      task.setProviderModelId(runtime.getProviderModelId());
      task.setEmbeddingDimension(runtime.getEmbeddingDimension());
      task.setBaseUrl(runtime.getBaseUrl());
      task.setApiKeyCiphertext(runtime.getApiKey());
      enriched.add(task);
    }
    return enriched;
  }

  private void validateTenant(Tenant tenant) {
    if (tenant == null || tenant.getTenantId() == null || tenant.getTenantId() <= 0) {
      throw new InternalException("tenantId is invalid");
    }
    if (!StringUtils.hasText(tenant.getTenantCode())) {
      throw new InternalException("tenantCode is invalid");
    }
  }

  private void validateRuntime(EmbeddingBindingMapper.RuntimeModelRow runtime) {
    if (runtime == null) {
      throw new InternalException("embedding runtime is missing");
    }
    if (runtime.getAiModelId() == null || runtime.getAiModelId() <= 0) {
      throw new InternalException("embedding runtime aiModelId is invalid");
    }
    if (!StringUtils.hasText(runtime.getProviderCode())) {
      throw new InternalException("embedding runtime providerCode is invalid");
    }
    if (!StringUtils.hasText(runtime.getProviderModelId())) {
      throw new InternalException("embedding runtime providerModelId is invalid");
    }
    if (!StringUtils.hasText(runtime.getApiKey())) {
      throw new InternalException("embedding runtime apiKey is invalid");
    }
  }
}
