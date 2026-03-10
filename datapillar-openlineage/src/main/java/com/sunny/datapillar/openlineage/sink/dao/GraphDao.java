package com.sunny.datapillar.openlineage.sink.dao;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.config.Neo4jConfig;
import com.sunny.datapillar.openlineage.model.Tenant;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Pure Neo4j graph DAO for graph node and relationship persistence. */
@Repository
public class GraphDao {

  private static final java.util.Set<String> SUPPORTED_LABELS =
      java.util.Set.of(
          "Catalog",
          "Schema",
          "Table",
          "Column",
          "Tag",
          "AtomicMetric",
          "DerivedMetric",
          "CompositeMetric",
          "WordRoot",
          "Modifier",
          "Unit",
          "ValueDomain",
          "SQL");

  private final Driver driver;
  private final Neo4jConfig.Neo4jProperties neo4jProperties;

  public GraphDao(Driver driver, Neo4jConfig.Neo4jProperties neo4jProperties) {
    this.driver = driver;
    this.neo4jProperties = neo4jProperties;
  }

  public void writeGraph(Tenant tenant, List<NodeWrite> nodes, List<LinkWrite> links) {
    validateTenant(tenant);
    try (Session session = newSession()) {
      session.executeWriteWithoutResult(
          tx -> {
            ensureTenant(tx, tenant);
            for (NodeWrite node : safe(nodes)) {
              upsertKnowledgeNode(
                  tx,
                  tenant.getTenantId(),
                  node.label(),
                  node.nodeId(),
                  node.name(),
                  node.description(),
                  node.extraProperties());
            }
            for (LinkWrite link : safe(links)) {
              linkNodes(tx, link.fromId(), link.toId(), link.relationshipType());
            }
          });
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Throwable ex) {
      throw new InternalException(ex, "Write graph failed");
    }
  }

  private void validateTenant(Tenant tenant) {
    if (tenant == null || tenant.getTenantId() == null || tenant.getTenantId() <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
  }

  private void ensureTenant(TransactionContext tx, Tenant tenant) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("tenantId", tenant.getTenantId());
    parameters.put("tenantCode", trimToNull(tenant.getTenantCode()));
    parameters.put("tenantName", trimToNull(tenant.getTenantName()));
    tx.run(
        new Query(
            """
            MERGE (t:Tenant {id:$tenantId})
            ON CREATE SET t.createdAt = datetime()
            SET t.code = CASE WHEN $tenantCode IS NULL THEN t.code ELSE $tenantCode END,
                t.name = CASE WHEN $tenantName IS NULL THEN t.name ELSE $tenantName END,
                t.updatedAt = datetime()
            """,
            parameters));
  }

  private void upsertKnowledgeNode(
      TransactionContext tx,
      Long tenantId,
      String label,
      String nodeId,
      String name,
      String description,
      Map<String, Object> extraProperties) {
    if (!SUPPORTED_LABELS.contains(label)) {
      throw new BadRequestException("Unsupported label: %s", label);
    }

    Map<String, Object> properties = new HashMap<>();
    properties.put("name", nonBlank(name, nodeId));
    properties.put("description", description == null ? "" : description);
    properties.put("updatedAtEpoch", Instant.now().toEpochMilli());
    if (extraProperties != null) {
      properties.putAll(extraProperties);
    }

    tx.run(
        new Query(
            """
            MATCH (t:Tenant {id:$tenantId})
            MERGE (n:Knowledge:%s {id:$nodeId})
            ON CREATE SET n.createdAt = datetime()
            SET n += $properties,
                n.updatedAt = datetime()
            MERGE (t)-[:OWNS]->(n)
            """
                .formatted(label),
            Map.of("tenantId", tenantId, "nodeId", nodeId, "properties", properties)));
  }

  private void linkNodes(
      TransactionContext tx, String fromId, String toId, String relationshipType) {
    if (!StringUtils.hasText(fromId)
        || !StringUtils.hasText(toId)
        || Objects.equals(fromId, toId)
        || !StringUtils.hasText(relationshipType)) {
      return;
    }
    tx.run(
        new Query(
            """
            MATCH (from:Knowledge {id:$fromId})
            MATCH (to:Knowledge {id:$toId})
            MERGE (from)-[:%s]->(to)
            """
                .formatted(relationshipType),
            Map.of("fromId", fromId, "toId", toId)));
  }

  private String nonBlank(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private <T> List<T> safe(List<T> rows) {
    return rows == null ? List.of() : rows;
  }

  private Session newSession() {
    return driver.session(SessionConfig.forDatabase(neo4jProperties.getDatabase()));
  }

  public record NodeWrite(
      String label,
      String nodeId,
      String name,
      String description,
      Map<String, Object> extraProperties) {}

  public record LinkWrite(String fromId, String toId, String relationshipType) {}
}
