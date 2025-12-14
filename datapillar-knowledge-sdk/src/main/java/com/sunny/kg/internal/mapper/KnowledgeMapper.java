package com.sunny.kg.internal.mapper;

import com.sunny.kg.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱映射器
 * <p>
 * 将业务模型转换为 Cypher 语句
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class KnowledgeMapper {

    private final String producer;

    public KnowledgeMapper(String producer) {
        this.producer = producer;
    }

    /**
     * 映射表元数据
     */
    public List<CypherStatement> mapTable(TableMeta table) {
        List<CypherStatement> statements = new ArrayList<>();

        // 1. 创建/更新 Table 节点
        Map<String, Object> tableParams = new HashMap<>();
        tableParams.put("name", table.getName());
        tableParams.put("displayName", table.getDisplayName() != null ? table.getDisplayName() : table.getName());
        tableParams.put("description", table.getDescription());
        tableParams.put("qualityScore", table.getQualityScore());
        tableParams.put("certificationLevel", table.getCertificationLevel());
        tableParams.put("businessValue", table.getBusinessValue());
        tableParams.put("sampleData", table.getSampleData());
        tableParams.put("tags", table.getTags());
        tableParams.put("producer", producer);

        statements.add(new CypherStatement("""
            MERGE (t:Table:Knowledge {name: $name})
            ON CREATE SET
                t.displayName = $displayName,
                t.description = $description,
                t.qualityScore = $qualityScore,
                t.certificationLevel = $certificationLevel,
                t.businessValue = $businessValue,
                t.sampleData = $sampleData,
                t.tags = $tags,
                t.embedding = [],
                t.createdBy = $producer,
                t.generatedAt = datetime(),
                t.confidence = 1.0
            ON MATCH SET
                t.displayName = COALESCE($displayName, t.displayName),
                t.description = COALESCE($description, t.description),
                t.qualityScore = COALESCE($qualityScore, t.qualityScore),
                t.certificationLevel = COALESCE($certificationLevel, t.certificationLevel),
                t.businessValue = COALESCE($businessValue, t.businessValue),
                t.sampleData = COALESCE($sampleData, t.sampleData),
                t.tags = COALESCE($tags, t.tags),
                t.updatedAt = datetime()
            """, tableParams));

        // 2. 关联到 Schema（如果指定）
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            Map<String, Object> schemaParams = Map.of(
                "tableName", table.getName(),
                "schemaLayer", table.getSchema()
            );
            statements.add(new CypherStatement("""
                MATCH (t:Table:Knowledge {name: $tableName})
                MATCH (s:Schema:Knowledge {layer: $schemaLayer})
                MERGE (s)-[:CONTAINS]->(t)
                """, schemaParams));
        }

        // 3. 创建字段节点
        if (table.getColumns() != null) {
            for (ColumnMeta column : table.getColumns()) {
                statements.addAll(mapColumn(table.getName(), column));
            }
        }

        return statements;
    }

    /**
     * 映射字段元数据
     */
    public List<CypherStatement> mapColumn(String tableName, ColumnMeta column) {
        List<CypherStatement> statements = new ArrayList<>();

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", tableName);
        params.put("columnName", column.getName());
        params.put("displayName", column.getDisplayName() != null ? column.getDisplayName() : column.getName());
        params.put("dataType", column.getDataType());
        params.put("description", column.getDescription());
        params.put("sampleData", column.getSampleData());
        params.put("producer", producer);

        statements.add(new CypherStatement("""
            MATCH (t:Table:Knowledge {name: $tableName})
            MERGE (c:Column:Knowledge {name: $columnName})<-[:HAS_COLUMN]-(t)
            ON CREATE SET
                c.displayName = $displayName,
                c.dataType = $dataType,
                c.description = $description,
                c.sampleData = $sampleData,
                c.embedding = [],
                c.createdBy = $producer,
                c.generatedAt = datetime(),
                c.confidence = 1.0
            ON MATCH SET
                c.displayName = COALESCE($displayName, c.displayName),
                c.dataType = COALESCE($dataType, c.dataType),
                c.description = COALESCE($description, c.description),
                c.sampleData = COALESCE($sampleData, c.sampleData),
                c.updatedAt = datetime()
            """, params));

        return statements;
    }

    /**
     * 映射目录元数据
     */
    public List<CypherStatement> mapCatalog(CatalogMeta catalog) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", catalog.getName());
        params.put("displayName", catalog.getDisplayName() != null ? catalog.getDisplayName() : catalog.getName());
        params.put("description", catalog.getDescription());
        params.put("dataScope", catalog.getDataScope());
        params.put("tags", catalog.getTags());
        params.put("producer", producer);

        return List.of(new CypherStatement("""
            MERGE (c:Catalog:Knowledge {name: $name})
            ON CREATE SET
                c.displayName = $displayName,
                c.description = $description,
                c.dataScope = $dataScope,
                c.tags = $tags,
                c.embedding = [],
                c.createdBy = $producer,
                c.generatedAt = datetime(),
                c.confidence = 1.0
            ON MATCH SET
                c.displayName = COALESCE($displayName, c.displayName),
                c.description = COALESCE($description, c.description),
                c.dataScope = COALESCE($dataScope, c.dataScope),
                c.tags = COALESCE($tags, c.tags),
                c.updatedAt = datetime()
            """, params));
    }

    /**
     * 映射分层元数据
     */
    public List<CypherStatement> mapSchema(SchemaMeta schema) {
        Map<String, Object> params = new HashMap<>();
        params.put("layer", schema.getLayer());
        params.put("displayName", schema.getDisplayName() != null ? schema.getDisplayName() : schema.getLayer());
        params.put("description", schema.getDescription());
        params.put("producer", producer);

        return List.of(new CypherStatement("""
            MERGE (s:Schema:Knowledge {layer: $layer})
            ON CREATE SET
                s.name = $layer,
                s.displayName = $displayName,
                s.description = $description,
                s.embedding = [],
                s.createdBy = $producer,
                s.generatedAt = datetime(),
                s.confidence = 1.0
            ON MATCH SET
                s.displayName = COALESCE($displayName, s.displayName),
                s.description = COALESCE($description, s.description),
                s.updatedAt = datetime()
            """, params));
    }

    /**
     * 映射血缘
     */
    public List<CypherStatement> mapLineage(Lineage lineage) {
        List<CypherStatement> statements = new ArrayList<>();

        // 1. 表级血缘
        Map<String, Object> tableLineageParams = new HashMap<>();
        tableLineageParams.put("sourceTable", lineage.getSourceTable());
        tableLineageParams.put("targetTable", lineage.getTargetTable());
        tableLineageParams.put("transformationType", lineage.getTransformationType());
        tableLineageParams.put("producer", producer);

        statements.add(new CypherStatement("""
            MATCH (source:Table:Knowledge {name: $sourceTable})
            MATCH (target:Table:Knowledge {name: $targetTable})
            MERGE (target)-[r:DERIVED_FROM]->(source)
            ON CREATE SET
                r.transformationType = $transformationType,
                r.createdBy = $producer,
                r.generatedAt = datetime()
            ON MATCH SET
                r.transformationType = COALESCE($transformationType, r.transformationType),
                r.updatedAt = datetime()
            """, tableLineageParams));

        // 2. 列级血缘
        if (lineage.getColumnLineages() != null) {
            for (ColumnLineage colLineage : lineage.getColumnLineages()) {
                statements.addAll(mapColumnLineage(lineage.getTargetTable(), colLineage));
            }
        }

        return statements;
    }

    /**
     * 映射列级血缘
     */
    public List<CypherStatement> mapColumnLineage(String targetTable, ColumnLineage colLineage) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceTable", colLineage.getSourceTable());
        params.put("sourceColumn", colLineage.getSourceColumn());
        params.put("targetTable", targetTable);
        params.put("targetColumn", colLineage.getTargetColumn());
        params.put("transformationType", colLineage.getTransformationType());
        params.put("transformationFunction", colLineage.getTransformationFunction());
        params.put("producer", producer);

        return List.of(new CypherStatement("""
            MATCH (sc:Column:Knowledge {name: $sourceColumn})<-[:HAS_COLUMN]-(st:Table {name: $sourceTable})
            MATCH (tc:Column:Knowledge {name: $targetColumn})<-[:HAS_COLUMN]-(tt:Table {name: $targetTable})
            MERGE (tc)-[r:DERIVED_FROM]->(sc)
            ON CREATE SET
                r.transformationType = $transformationType,
                r.transformationFunction = $transformationFunction,
                r.createdBy = $producer,
                r.generatedAt = datetime()
            ON MATCH SET
                r.transformationType = COALESCE($transformationType, r.transformationType),
                r.transformationFunction = COALESCE($transformationFunction, r.transformationFunction),
                r.updatedAt = datetime()
            """, params));
    }

    /**
     * 映射指标
     */
    public List<CypherStatement> mapMetric(MetricMeta metric) {
        List<CypherStatement> statements = new ArrayList<>();

        String label = switch (metric.getType()) {
            case ATOMIC -> "AtomicMetric";
            case DERIVED -> "DerivedMetric";
            case COMPOSITE -> "CompositeMetric";
        };

        Map<String, Object> params = new HashMap<>();
        params.put("name", metric.getName());
        params.put("displayName", metric.getDisplayName() != null ? metric.getDisplayName() : metric.getName());
        params.put("description", metric.getDescription());
        params.put("formula", metric.getFormula());
        params.put("formulaExpression", metric.getFormulaExpression());
        params.put("unit", metric.getUnit());
        params.put("category", metric.getCategory());
        params.put("metricType", metric.getMetricAggType());
        params.put("timeModifier", metric.getTimeModifier());
        params.put("businessImportance", metric.getBusinessImportance());
        params.put("certificationLevel", metric.getCertificationLevel());
        params.put("producer", producer);

        String cypher = String.format("""
            MERGE (m:%s:Knowledge {name: $name})
            ON CREATE SET
                m.displayName = $displayName,
                m.description = $description,
                m.formula = $formula,
                m.formulaExpression = $formulaExpression,
                m.unit = $unit,
                m.category = $category,
                m.metricType = $metricType,
                m.timeModifier = $timeModifier,
                m.businessImportance = $businessImportance,
                m.certificationLevel = $certificationLevel,
                m.embedding = [],
                m.createdBy = $producer,
                m.generatedAt = datetime(),
                m.confidence = 1.0
            ON MATCH SET
                m.displayName = COALESCE($displayName, m.displayName),
                m.description = COALESCE($description, m.description),
                m.formula = COALESCE($formula, m.formula),
                m.updatedAt = datetime()
            """, label);

        statements.add(new CypherStatement(cypher, params));

        // 原子指标绑定字段
        if (metric.getType() == MetricMeta.MetricType.ATOMIC
                && metric.getBoundColumn() != null
                && metric.getBoundTable() != null) {
            Map<String, Object> bindParams = Map.of(
                "metricName", metric.getName(),
                "tableName", metric.getBoundTable(),
                "columnName", metric.getBoundColumn()
            );
            statements.add(new CypherStatement("""
                MATCH (m:AtomicMetric:Knowledge {name: $metricName})
                MATCH (c:Column:Knowledge {name: $columnName})<-[:HAS_COLUMN]-(t:Table {name: $tableName})
                MERGE (m)-[:MEASURES]->(c)
                """, bindParams));
        }

        // 派生/复合指标来源
        if (metric.getSourceMetrics() != null && !metric.getSourceMetrics().isEmpty()) {
            for (String sourceMetric : metric.getSourceMetrics()) {
                Map<String, Object> sourceParams = Map.of(
                    "metricName", metric.getName(),
                    "sourceMetricName", sourceMetric
                );
                String relType = metric.getType() == MetricMeta.MetricType.DERIVED ? "DERIVED_FROM" : "COMPUTED_FROM";
                statements.add(new CypherStatement(String.format("""
                    MATCH (m:Knowledge {name: $metricName})
                    MATCH (sm:Knowledge {name: $sourceMetricName})
                    WHERE (m:DerivedMetric OR m:CompositeMetric) AND (sm:AtomicMetric OR sm:DerivedMetric)
                    MERGE (m)-[:%s]->(sm)
                    """, relType), sourceParams));
            }
        }

        return statements;
    }

    /**
     * 映射质量规则
     */
    public List<CypherStatement> mapQualityRule(QualityRuleMeta rule) {
        List<CypherStatement> statements = new ArrayList<>();

        Map<String, Object> params = new HashMap<>();
        params.put("name", rule.getName());
        params.put("displayName", rule.getDisplayName() != null ? rule.getDisplayName() : rule.getName());
        params.put("description", rule.getDescription());
        params.put("ruleType", rule.getRuleType());
        params.put("sqlExp", rule.getExpression());
        params.put("isRequired", rule.getRequired());
        params.put("severity", rule.getSeverity());
        params.put("isEnabled", rule.getEnabled());
        params.put("producer", producer);

        statements.add(new CypherStatement("""
            MERGE (q:QualityRule:Knowledge {name: $name})
            ON CREATE SET
                q.displayName = $displayName,
                q.description = $description,
                q.ruleType = $ruleType,
                q.sqlExp = $sqlExp,
                q.isRequired = $isRequired,
                q.severity = $severity,
                q.isEnabled = $isEnabled,
                q.embedding = [],
                q.createdBy = $producer,
                q.generatedAt = datetime(),
                q.confidence = 1.0
            ON MATCH SET
                q.displayName = COALESCE($displayName, q.displayName),
                q.description = COALESCE($description, q.description),
                q.ruleType = COALESCE($ruleType, q.ruleType),
                q.sqlExp = COALESCE($sqlExp, q.sqlExp),
                q.updatedAt = datetime()
            """, params));

        // 绑定到字段
        if (rule.getBoundColumn() != null && rule.getBoundTable() != null) {
            Map<String, Object> bindParams = new HashMap<>();
            bindParams.put("ruleName", rule.getName());
            bindParams.put("tableName", rule.getBoundTable());
            bindParams.put("columnName", rule.getBoundColumn());
            bindParams.put("priority", rule.getPriority());
            bindParams.put("producer", producer);

            statements.add(new CypherStatement("""
                MATCH (q:QualityRule:Knowledge {name: $ruleName})
                MATCH (c:Column:Knowledge {name: $columnName})<-[:HAS_COLUMN]-(t:Table {name: $tableName})
                MERGE (c)-[r:HAS_QUALITY_RULE]->(q)
                ON CREATE SET
                    r.priority = $priority,
                    r.createdBy = $producer,
                    r.generatedAt = datetime()
                """, bindParams));
        }

        return statements;
    }

}
