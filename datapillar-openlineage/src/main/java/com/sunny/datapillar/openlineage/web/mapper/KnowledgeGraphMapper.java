package com.sunny.datapillar.openlineage.web.mapper;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.config.Neo4jConfig;
import com.sunny.datapillar.openlineage.web.dto.response.GraphNodeView;
import com.sunny.datapillar.openlineage.web.dto.response.GraphRelationshipView;
import com.sunny.datapillar.openlineage.web.dto.response.InitialGraphResponse;
import com.sunny.datapillar.openlineage.web.dto.response.SearchNodeResult;
import com.sunny.datapillar.openlineage.web.dto.response.Text2CypherResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** API-side graph query mapper. */
@Component
public class KnowledgeGraphMapper {

  private static final int DEFAULT_SEARCH_TOP_K = 10;
  private static final int MAX_SEARCH_TOP_K = 50;
  private static final double DEFAULT_SEARCH_SCORE_THRESHOLD = 0.0D;
  private static final String VECTOR_SEARCH_CYPHER =
      """
      CALL db.index.vector.queryNodes('kg_global_embedding_idx', $topK, $queryVector)
      YIELD node, score
      MATCH (t:Tenant {id:$tenantId})-[:OWNS]->(node)
      WHERE score >= $scoreThreshold
      RETURN node.id AS id, labels(node) AS labels, score AS score, properties(node) AS properties
      ORDER BY score DESC
      LIMIT $topK
      """;

  private final Driver driver;
  private final Neo4jConfig.Neo4jProperties neo4jProperties;

  public KnowledgeGraphMapper(Driver driver, Neo4jConfig.Neo4jProperties neo4jProperties) {
    this.driver = driver;
    this.neo4jProperties = neo4jProperties;
  }

  public InitialGraphResponse loadInitialGraph(Long tenantId, int limit) {
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
    int safeLimit = Math.max(1, limit);
    try (Session session = newSession()) {
      Result nodeResult =
          session.run(
              new Query(
                  """
                  MATCH (t:Tenant {id:$tenantId})-[:OWNS]->(n:Knowledge)
                  RETURN n.id AS id, labels(n) AS labels, properties(n) AS properties
                  LIMIT $limit
                  """,
                  Map.of("tenantId", tenantId, "limit", safeLimit)));

      List<GraphNodeView> nodes = new ArrayList<>();
      Set<String> nodeIds = new HashSet<>();
      while (nodeResult.hasNext()) {
        var row = nodeResult.next();
        String nodeId = row.get("id").asString();
        nodeIds.add(nodeId);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) row.get("properties").asObject();
        List<String> labels = row.get("labels").asList(org.neo4j.driver.Value::asString);
        String type = resolvePrimaryLabel(labels);
        nodes.add(new GraphNodeView(nodeId, type, resolveLevel(type), properties));
      }

      Result relationResult =
          session.run(
              new Query(
                  """
                  MATCH (t:Tenant {id:$tenantId})-[:OWNS]->(start:Knowledge)-[r]->(end:Knowledge)
                  MATCH (t)-[:OWNS]->(end)
                  RETURN elementId(r) AS relId, type(r) AS relType, start.id AS startId, end.id AS endId
                  LIMIT $limit
                  """,
                  Map.of("tenantId", tenantId, "limit", safeLimit)));

      List<GraphRelationshipView> relations = new ArrayList<>();
      while (relationResult.hasNext()) {
        var row = relationResult.next();
        String startId = row.get("startId").asString();
        String endId = row.get("endId").asString();
        if (!nodeIds.contains(startId) || !nodeIds.contains(endId)) {
          continue;
        }
        relations.add(
            new GraphRelationshipView(
                row.get("relId").asString(), row.get("relType").asString(), startId, endId));
      }

      return new InitialGraphResponse(tenantId, nodes, relations);
    }
  }

  public Text2CypherResponse queryByText(Long tenantId, String query, Integer limit) {
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
    if (!StringUtils.hasText(query)) {
      throw new BadRequestException("query is empty");
    }

    int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 200);
    String cypher =
        "MATCH (t:Tenant {id:$tenantId})-[:OWNS]->(n:Knowledge) "
            + "WHERE toLower(coalesce(n.name,'')) CONTAINS toLower($query) "
            + "OR toLower(coalesce(n.description,'')) CONTAINS toLower($query) "
            + "RETURN n.id AS id, labels(n) AS labels, properties(n) AS properties LIMIT $limit";

    try (Session session = newSession()) {
      Result result =
          session.run(
              new Query(
                  cypher, Map.of("tenantId", tenantId, "query", query.trim(), "limit", safeLimit)));
      List<Map<String, Object>> rows = new ArrayList<>();
      while (result.hasNext()) {
        var row = result.next();
        rows.add(row.asMap(org.neo4j.driver.Value::asObject));
      }
      return new Text2CypherResponse(tenantId, query, cypher, rows);
    } catch (Throwable ex) {
      throw new InternalException(ex, "text2cypher execution failed");
    }
  }

  public List<SearchNodeResult> vectorSearch(
      Long tenantId, List<Double> queryVector, Integer topK, Double scoreThreshold) {
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
    if (queryVector == null || queryVector.isEmpty()) {
      throw new BadRequestException("queryVector is empty");
    }
    int safeTopK =
        topK == null || topK <= 0 ? DEFAULT_SEARCH_TOP_K : Math.min(topK, MAX_SEARCH_TOP_K);
    double safeThreshold = scoreThreshold == null ? DEFAULT_SEARCH_SCORE_THRESHOLD : scoreThreshold;

    try (Session session = newSession()) {
      Result result =
          session.run(
              new Query(
                  VECTOR_SEARCH_CYPHER,
                  Map.of(
                      "topK",
                      safeTopK,
                      "tenantId",
                      tenantId,
                      "scoreThreshold",
                      safeThreshold,
                      "queryVector",
                      queryVector)));

      List<SearchNodeResult> rows = new ArrayList<>();
      while (result.hasNext()) {
        var row = result.next();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) row.get("properties").asObject();
        String type =
            resolvePrimaryLabel(row.get("labels").asList(org.neo4j.driver.Value::asString));
        rows.add(
            new SearchNodeResult(
                row.get("id").asString(), type, row.get("score").asDouble(), properties));
      }
      return rows;
    } catch (Throwable ex) {
      throw new InternalException(ex, "Vector search failed");
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

  private Integer resolveLevel(String type) {
    return switch (type) {
      case "Catalog" -> 1;
      case "Schema" -> 2;
      case "Table" -> 3;
      case "Column" -> 4;
      case "SQL" -> 5;
      default -> 6;
    };
  }

  private Session newSession() {
    return driver.session(SessionConfig.forDatabase(neo4jProperties.getDatabase()));
  }
}
