package com.sunny.datapillar.openlineage.sink.dao;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.config.Neo4jConfig;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Neo4j sink for embedding write and vector retrieval. */
@Repository
public class VectorDao {

  private final Driver driver;
  private final Neo4jConfig.Neo4jProperties neo4jProperties;

  public VectorDao(Driver driver, Neo4jConfig.Neo4jProperties neo4jProperties) {
    this.driver = driver;
    this.neo4jProperties = neo4jProperties;
  }

  public void writeEmbedding(
      Long tenantId, String resourceId, List<Double> embedding, String provider, Long revision) {
    if (tenantId == null || tenantId <= 0 || !StringUtils.hasText(resourceId)) {
      throw new BadRequestException("Embedding write parameters are invalid");
    }
    try (Session session = newSession()) {
      session.executeWriteWithoutResult(
          tx ->
              tx.run(
                  new Query(
                      """
                      MATCH (t:Tenant {id:$tenantId})-[:OWNS]->(n:Knowledge {id:$resourceId})
                      WHERE n.embeddingRevision IS NULL OR n.embeddingRevision <= $revision
                      SET n.embedding = $embedding,
                          n.embeddingProvider = $provider,
                          n.embeddingRevision = $revision,
                          n.embeddingUpdatedAt = datetime(),
                          n.updatedAt = datetime()
                      """,
                      Map.of(
                          "tenantId",
                          tenantId,
                          "resourceId",
                          resourceId,
                          "embedding",
                          embedding,
                          "provider",
                          nonBlank(provider, "unknown"),
                          "revision",
                          revision == null ? 0L : revision))));
    } catch (Throwable ex) {
      throw new InternalException(ex, "Write embedding failed");
    }
  }

  public List<EmbeddingTaskPayload> listTenantEmbeddingTasks(Long tenantId, int limit, int offset) {
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
    try (Session session = newSession()) {
      Result result =
          session.run(
              new Query(
                  """
                  MATCH (t:Tenant {id:$tenantId})-[:OWNS]->(n:Knowledge)
                  RETURN n.id AS id,
                         labels(n) AS labels,
                         trim(coalesce(n.name,'') + ' ' + coalesce(n.description,'')) AS content
                  SKIP $offset
                  LIMIT $limit
                  """,
                  Map.of(
                      "tenantId",
                      tenantId,
                      "offset",
                      Math.max(0, offset),
                      "limit",
                      Math.max(1, limit))));
      List<EmbeddingTaskPayload> tasks = new ArrayList<>();
      while (result.hasNext()) {
        var row = result.next();
        String rowResourceId = row.get("id").asString();
        String content = nonBlank(row.get("content").asString(), rowResourceId);
        String resourceType =
            resolvePrimaryLabel(row.get("labels").asList(org.neo4j.driver.Value::asString));
        EmbeddingTaskPayload task = new EmbeddingTaskPayload();
        task.setResourceId(rowResourceId);
        task.setResourceType(resourceType);
        task.setContent(content);
        tasks.add(task);
      }
      return tasks;
    }
  }

  private String resolvePrimaryLabel(List<String> labels) {
    if (labels == null || labels.isEmpty()) {
      return "Knowledge";
    }
    for (String label : labels) {
      if (!"Knowledge".equals(label) && !"Tenant".equals(label)) {
        return label;
      }
    }
    return labels.getFirst();
  }

  private String nonBlank(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private Session newSession() {
    return driver.session(SessionConfig.forDatabase(neo4jProperties.getDatabase()));
  }
}
