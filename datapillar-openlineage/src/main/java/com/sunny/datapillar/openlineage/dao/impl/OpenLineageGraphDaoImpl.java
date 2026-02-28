package com.sunny.datapillar.openlineage.dao.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.openlineage.config.Neo4jConfig;
import com.sunny.datapillar.openlineage.dao.OpenLineageGraphDao;
import com.sunny.datapillar.openlineage.exception.OpenLineageWriteException;
import com.sunny.datapillar.openlineage.model.AsyncTaskCandidate;
import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.OpenLineageUpdateResult;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;

/**
 * OpenLineage 图谱 DAO 实现。
 */
@Slf4j
@Repository
public class OpenLineageGraphDaoImpl implements OpenLineageGraphDao {

    private static final String DEFAULT_EMBEDDING_MODEL = "builtin:embedding:v1";
    private static final String DEFAULT_SUMMARY_MODEL = "builtin:summary:v1";

    private final Driver driver;
    private final Neo4jConfig.Neo4jProperties neo4jProperties;

    public OpenLineageGraphDaoImpl(Driver driver, Neo4jConfig.Neo4jProperties neo4jProperties) {
        this.driver = driver;
        this.neo4jProperties = neo4jProperties;
    }

    @Override
    public OpenLineageUpdateResult updateDatapillarModel(OpenLineage.RunEvent event,
                                                         OpenLineageEventEnvelope envelope,
                                                         TenantContext tenantContext) {
        return updateGraph(envelope, tenantContext, true);
    }

    @Override
    public OpenLineageUpdateResult updateDatapillarModel(OpenLineage.DatasetEvent event,
                                                         OpenLineageEventEnvelope envelope,
                                                         TenantContext tenantContext) {
        return updateGraph(envelope, tenantContext, false);
    }

    @Override
    public OpenLineageUpdateResult updateDatapillarModel(OpenLineage.JobEvent event,
                                                         OpenLineageEventEnvelope envelope,
                                                         TenantContext tenantContext) {
        return updateGraph(envelope, tenantContext, false);
    }

    @Override
    public String fetchResourceContent(Long tenantId, String resourceType, String resourceId) {
        if (tenantId == null || tenantId <= 0 || resourceId == null || resourceId.isBlank()) {
            return "";
        }

        try (Session session = newSession()) {
            Map<String, Object> params = Map.of(
                    "tenantId", tenantId,
                    "resourceId", resourceId,
                    "resourceType", resourceType == null ? "" : resourceType.toUpperCase());

            Result result = session.run("""
                    MATCH (n {tenantId: $tenantId, id: $resourceId})
                    RETURN CASE
                        WHEN $resourceType = 'SQL' THEN coalesce(n.content, '')
                        ELSE trim(coalesce(n.name, '') + ' ' + coalesce(n.description, '') + ' ' + coalesce(n.content, ''))
                    END AS content
                    LIMIT 1
                    """, params);

            if (!result.hasNext()) {
                return "";
            }
            return result.next().get("content").asString("");
        }
    }

    @Override
    public void writeEmbedding(Long tenantId, String resourceId, String provider, double[] embedding) {
        if (tenantId == null || tenantId <= 0 || resourceId == null || resourceId.isBlank()) {
            return;
        }

        try (Session session = newSession()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (n {tenantId: $tenantId, id: $resourceId})
                        SET n.embedding = $embedding,
                            n.embeddingProvider = $provider,
                            n.embeddingUpdatedAt = datetime(),
                            n.updatedAt = datetime()
                        """, Map.of(
                        "tenantId", tenantId,
                        "resourceId", resourceId,
                        "provider", provider == null ? "default" : provider,
                        "embedding", toDoubleList(embedding)));
                return null;
            });
        }
    }

    @Override
    public void writeSqlSummary(Long tenantId,
                                String resourceId,
                                String summary,
                                String tags,
                                String provider,
                                double[] embedding) {
        if (tenantId == null || tenantId <= 0 || resourceId == null || resourceId.isBlank()) {
            return;
        }

        try (Session session = newSession()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (n:SQL {tenantId: $tenantId, id: $resourceId})
                        SET n.summary = $summary,
                            n.tags = $tags,
                            n.summaryGeneratedAt = datetime(),
                            n.embedding = $embedding,
                            n.embeddingProvider = $provider,
                            n.embeddingUpdatedAt = datetime(),
                            n.updatedAt = datetime()
                        """, Map.of(
                        "tenantId", tenantId,
                        "resourceId", resourceId,
                        "summary", summary == null ? "" : summary,
                        "tags", tags == null ? "" : tags,
                        "provider", provider == null ? "default" : provider,
                        "embedding", toDoubleList(embedding)));
                return null;
            });
        }
    }

    private OpenLineageUpdateResult updateGraph(OpenLineageEventEnvelope envelope,
                                                TenantContext tenantContext,
                                                boolean includeSqlLineage) {
        validateTenant(tenantContext);

        try (Session session = newSession()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MERGE (t:Tenant {id: $tenantId})
                        ON CREATE SET t.createdAt = datetime()
                        SET t.code = $tenantCode,
                            t.name = $tenantName,
                            t.updatedAt = datetime()
                        """, Map.of(
                        "tenantId", tenantContext.tenantId(),
                        "tenantCode", tenantContext.tenantCode(),
                        "tenantName", tenantContext.tenantName()));
                return null;
            });

            List<AssetRef> inputRefs = upsertDatasets(session, envelope.inputDatasetNodes(), tenantContext);
            List<AssetRef> outputRefs = upsertDatasets(session, envelope.outputDatasetNodes(), tenantContext);

            OpenLineageUpdateResult.Builder resultBuilder = OpenLineageUpdateResult.builder();
            Set<String> taskDedup = new HashSet<>();
            collectEmbeddingTasks(inputRefs, tenantContext, resultBuilder, taskDedup, envelope.payloadFingerprint());
            collectEmbeddingTasks(outputRefs, tenantContext, resultBuilder, taskDedup, envelope.payloadFingerprint());

            String sql = envelope.sqlQuery();
            if (includeSqlLineage && sql != null && !sql.isBlank()) {
                String sqlId = upsertSqlNode(session, envelope, tenantContext, sql);
                createSqlEdges(session, tenantContext, sqlId, inputRefs, outputRefs);

                String sqlFingerprint = stableId(sql + "|" + envelope.payloadFingerprint());
                resultBuilder.addCandidate(AsyncTaskCandidate.builder()
                        .taskType(AsyncTaskType.SQL_SUMMARY)
                        .resourceType("SQL")
                        .resourceId(sqlId)
                        .contentHash(sqlFingerprint)
                        .modelFingerprint(DEFAULT_SUMMARY_MODEL)
                        .payload(sql)
                        .build());
                resultBuilder.addCandidate(AsyncTaskCandidate.builder()
                        .taskType(AsyncTaskType.EMBEDDING)
                        .resourceType("SQL")
                        .resourceId(sqlId)
                        .contentHash(sqlFingerprint)
                        .modelFingerprint(DEFAULT_EMBEDDING_MODEL)
                        .payload(sql)
                        .build());
            }

            return resultBuilder.build();
        } catch (Exception ex) {
            throw new OpenLineageWriteException(ex, "写入 Neo4j 图谱失败");
        }
    }

    private List<AssetRef> upsertDatasets(Session session, List<JsonNode> datasets, TenantContext tenantContext) {
        if (datasets == null || datasets.isEmpty()) {
            return List.of();
        }

        List<AssetRef> refs = new ArrayList<>();
        for (JsonNode dataset : datasets) {
            AssetRef ref = parseDataset(dataset, tenantContext.tenantId());
            if (ref == null) {
                continue;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("tenantId", tenantContext.tenantId());
            params.put("tenantCode", tenantContext.tenantCode());
            params.put("catalogId", ref.catalogId());
            params.put("catalogName", ref.catalogName());
            params.put("schemaId", ref.schemaId());
            params.put("schemaName", ref.schemaName());
            params.put("tableId", ref.tableId());
            params.put("tableName", ref.tableName());

            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (tenant:Tenant {id: $tenantId})
                        MERGE (catalog:Catalog {tenantId: $tenantId, id: $catalogId})
                        ON CREATE SET catalog.createdAt = datetime()
                        SET catalog.name = $catalogName,
                            catalog.updatedAt = datetime()
                        MERGE (tenant)-[:OWNS_CATALOG]->(catalog)

                        MERGE (schema:Schema {tenantId: $tenantId, id: $schemaId})
                        ON CREATE SET schema.createdAt = datetime()
                        SET schema.name = $schemaName,
                            schema.updatedAt = datetime()
                        MERGE (catalog)-[:HAS_SCHEMA]->(schema)

                        MERGE (table:Table {tenantId: $tenantId, id: $tableId})
                        ON CREATE SET table.createdAt = datetime()
                        SET table.name = $tableName,
                            table.updatedAt = datetime()
                        MERGE (schema)-[:HAS_TABLE]->(table)
                        """, params);
                return null;
            });
            refs.add(ref);
        }
        return refs;
    }

    private String upsertSqlNode(Session session,
                                 OpenLineageEventEnvelope envelope,
                                 TenantContext tenantContext,
                                 String sql) {
        String sqlId = stableId(tenantContext.tenantId() + "|sql|" + envelope.jobNamespace() + "|" + envelope.jobName() + "|" + sql);
        Map<String, Object> params = Map.of(
                "tenantId", tenantContext.tenantId(),
                "sqlId", sqlId,
                "content", sql,
                "jobNamespace", defaultText(envelope.jobNamespace(), "unknown"),
                "jobName", defaultText(envelope.jobName(), "unknown"));

        session.executeWrite(tx -> {
            tx.run("""
                    MATCH (tenant:Tenant {id: $tenantId})
                    MERGE (sql:SQL {tenantId: $tenantId, id: $sqlId})
                    ON CREATE SET sql.createdAt = datetime()
                    SET sql.content = $content,
                        sql.jobNamespace = $jobNamespace,
                        sql.jobName = $jobName,
                        sql.updatedAt = datetime()
                    MERGE (tenant)-[:OWNS_SQL]->(sql)
                    """, params);
            return null;
        });
        return sqlId;
    }

    private void createSqlEdges(Session session,
                                TenantContext tenantContext,
                                String sqlId,
                                List<AssetRef> inputRefs,
                                List<AssetRef> outputRefs) {
        for (AssetRef input : inputRefs) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (tbl:Table {tenantId: $tenantId, id: $tableId})
                        MATCH (sql:SQL {tenantId: $tenantId, id: $sqlId})
                        MERGE (tbl)-[:INPUT_OF]->(sql)
                        """, Map.of(
                        "tenantId", tenantContext.tenantId(),
                        "tableId", input.tableId(),
                        "sqlId", sqlId));
                return null;
            });
        }

        for (AssetRef output : outputRefs) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (tbl:Table {tenantId: $tenantId, id: $tableId})
                        MATCH (sql:SQL {tenantId: $tenantId, id: $sqlId})
                        MERGE (sql)-[:OUTPUT_TO]->(tbl)
                        """, Map.of(
                        "tenantId", tenantContext.tenantId(),
                        "tableId", output.tableId(),
                        "sqlId", sqlId));
                return null;
            });
        }
    }

    private void collectEmbeddingTasks(List<AssetRef> refs,
                                       TenantContext tenantContext,
                                       OpenLineageUpdateResult.Builder resultBuilder,
                                       Set<String> taskDedup,
                                       String baseFingerprint) {
        for (AssetRef ref : refs) {
            String dedupKey = "TABLE|" + ref.tableId();
            if (!taskDedup.add(dedupKey)) {
                continue;
            }
            String contentHash = stableId(baseFingerprint + "|" + ref.tableId());
            resultBuilder.addCandidate(AsyncTaskCandidate.builder()
                    .taskType(AsyncTaskType.EMBEDDING)
                    .resourceType("TABLE")
                    .resourceId(ref.tableId())
                    .contentHash(contentHash)
                    .modelFingerprint(DEFAULT_EMBEDDING_MODEL)
                    .payload(ref.tableName())
                    .build());
        }
    }

    private AssetRef parseDataset(JsonNode dataset, Long tenantId) {
        if (dataset == null || !dataset.isObject()) {
            return null;
        }

        String namespace = trimToNull(dataset.path("namespace").asText(null));
        String name = trimToNull(dataset.path("name").asText(null));
        if (name == null) {
            return null;
        }

        String catalogName = parseCatalog(namespace);
        String[] schemaAndTable = splitSchemaAndTable(name);
        String schemaName = schemaAndTable[0];
        String tableName = schemaAndTable[1];

        String catalogId = stableId(tenantId + "|catalog|" + catalogName);
        String schemaId = stableId(tenantId + "|schema|" + catalogName + "|" + schemaName);
        String tableId = stableId(tenantId + "|table|" + catalogName + "|" + schemaName + "|" + tableName);
        return new AssetRef(catalogId, schemaId, tableId, catalogName, schemaName, tableName);
    }

    private String parseCatalog(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "default_catalog";
        }
        String normalized = namespace;
        int protocolSep = normalized.indexOf("://");
        if (protocolSep >= 0) {
            normalized = normalized.substring(protocolSep + 3);
        }
        String[] parts = normalized.split("/");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String text = trimToNull(part);
            if (text != null) {
                tokens.add(text);
            }
        }
        if (tokens.isEmpty()) {
            return "default_catalog";
        }
        return tokens.getLast();
    }

    private String[] splitSchemaAndTable(String datasetName) {
        String normalized = datasetName.trim();
        int idx = normalized.lastIndexOf('.');
        if (idx > 0 && idx < normalized.length() - 1) {
            return new String[]{normalized.substring(0, idx), normalized.substring(idx + 1)};
        }
        return new String[]{"default_schema", normalized};
    }

    private String stableId(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<Double> toDoubleList(double[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(embedding.length);
        for (double value : embedding) {
            values.add(value);
        }
        return values;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private void validateTenant(TenantContext tenantContext) {
        if (tenantContext == null || tenantContext.tenantId() == null || tenantContext.tenantId() <= 0) {
            throw new OpenLineageWriteException("缺少租户ID");
        }
        if (tenantContext.tenantCode() == null || tenantContext.tenantCode().isBlank()) {
            throw new OpenLineageWriteException("缺少租户编码");
        }
        if (tenantContext.tenantName() == null || tenantContext.tenantName().isBlank()) {
            throw new OpenLineageWriteException("缺少租户名称");
        }
    }

    private Session newSession() {
        return driver.session(SessionConfig.forDatabase(neo4jProperties.getDatabase()));
    }

    private record AssetRef(
            String catalogId,
            String schemaId,
            String tableId,
            String catalogName,
            String schemaName,
            String tableName
    ) {
    }
}
