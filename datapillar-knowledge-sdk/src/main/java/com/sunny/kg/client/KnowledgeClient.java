package com.sunny.kg.client;

import com.sunny.kg.health.KnowledgeHealth;
import com.sunny.kg.metrics.KnowledgeMetrics;
import com.sunny.kg.model.*;

import java.time.Duration;
import java.util.List;

/**
 * 知识库客户端接口
 * <p>
 * 提供统一的知识图谱数据写入能力，支持多种数据源接入：
 * <ul>
 *     <li>Gravitino 元数据同步</li>
 *     <li>datapillar-job 血缘采集</li>
 *     <li>Excel 导入</li>
 *     <li>自定义数据源</li>
 * </ul>
 *
 * @author Sunny
 * @since 2025-12-10
 */
public interface KnowledgeClient extends AutoCloseable {

    // ==================== 表元数据 ====================

    /**
     * 写入表元数据（包含字段）
     *
     * @param table 表元数据
     */
    void emitTable(TableMeta table);

    /**
     * 批量写入表元数据
     *
     * @param tables 表元数据列表
     */
    void emitTables(List<TableMeta> tables);

    // ==================== 目录和分层 ====================

    /**
     * 写入数据目录
     *
     * @param catalog 目录元数据
     */
    void emitCatalog(CatalogMeta catalog);

    /**
     * 写入数仓分层
     *
     * @param schema 分层元数据
     */
    void emitSchema(SchemaMeta schema);

    // ==================== 血缘 ====================

    /**
     * 写入表级血缘（含列级血缘）
     *
     * @param lineage 血缘信息
     */
    void emitLineage(Lineage lineage);

    /**
     * 批量写入血缘
     *
     * @param lineages 血缘列表
     */
    void emitLineages(List<Lineage> lineages);

    /**
     * 写入简单表级血缘
     *
     * @param sourceTable        来源表名
     * @param targetTable        目标表名
     * @param transformationType 转换类型
     */
    void emitLineage(String sourceTable, String targetTable, String transformationType);

    // ==================== 指标 ====================

    /**
     * 写入指标
     *
     * @param metric 指标元数据
     */
    void emitMetric(MetricMeta metric);

    /**
     * 批量写入指标
     *
     * @param metrics 指标列表
     */
    void emitMetrics(List<MetricMeta> metrics);

    // ==================== 质量规则 ====================

    /**
     * 写入质量规则
     *
     * @param rule 质量规则元数据
     */
    void emitQualityRule(QualityRuleMeta rule);

    /**
     * 批量写入质量规则
     *
     * @param rules 质量规则列表
     */
    void emitQualityRules(List<QualityRuleMeta> rules);

    // ==================== 通用操作 ====================

    /**
     * 刷新缓冲区，将所有待写入数据立即写入 Neo4j
     */
    void flush();

    /**
     * 关闭客户端，释放资源（使用默认超时 30s）
     */
    @Override
    void close();

    /**
     * 优雅关闭客户端（指定超时时间）
     *
     * @param timeout 超时时间
     * @return 是否在超时前完成
     */
    boolean close(Duration timeout);

    /**
     * 强制立即关闭
     */
    void closeNow();

    // ==================== 可观测性 ====================

    /**
     * 获取指标数据
     *
     * @return 指标接口
     */
    KnowledgeMetrics metrics();

    /**
     * 获取健康状态
     *
     * @return 健康检查接口
     */
    KnowledgeHealth health();

    // ==================== 静态工厂方法 ====================

    /**
     * 创建客户端构建器
     *
     * @return 构建器实例
     */
    static KnowledgeClientBuilder builder() {
        return new KnowledgeClientBuilder();
    }

}
