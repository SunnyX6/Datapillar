"""
Neo4j 知识图谱 Schema 初始化脚本
根据 docs/knowledge-graph-design-v3.md 创建约束、索引、向量索引和全文索引
版本：v3.0 - 以数据资产为中心的业务化知识图谱
"""

import os
from pathlib import Path

import logging
logger = logging.getLogger(__name__)
from neo4j import GraphDatabase
from neo4j_graphrag.indexes import create_fulltext_index
from sqlalchemy import create_engine, text


# 直接从环境变量读取配置，避免循环导入
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")

# MySQL配置（用于获取 embedding 维度）
MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "data_ai_builder")
MYSQL_USERNAME = os.getenv("MYSQL_USERNAME", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "Sunny.123456")


def get_embedding_dimension() -> int:
    """从 MySQL 获取当前默认 Embedding 模型的向量维度"""
    try:
        db_url = (
            f"mysql+pymysql://{MYSQL_USERNAME}:{MYSQL_PASSWORD}"
            f"@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}"
            f"?charset=utf8mb4"
        )
        engine = create_engine(db_url)

        with engine.connect() as conn:
            query = text("""
                SELECT embedding_dimension
                FROM ai_model
                WHERE is_enabled = 1 AND is_default = 1 AND model_type = 'embedding'
                LIMIT 1
            """)
            result = conn.execute(query)
            row = result.fetchone()

            if row and row[0]:
                dimension = int(row[0])
                logger.info(f"从数据库获取到 Embedding 维度: {dimension}")
                return dimension

        logger.warning("未找到默认 Embedding 模型，使用默认维度 2048（GLM embedding-3）")
        return 2048

    except Exception as e:
        logger.warning(f"无法从数据库获取 Embedding 维度，使用默认值 2048: {e}")
        return 2048


class KnowledgeGraphSchemaInitializer:
    """知识图谱 Schema 初始化器 - v3.0"""

    def __init__(self, driver, vector_dimension: int):
        self.driver = driver
        self.vector_dimension = vector_dimension

    def initialize(self):
        """初始化完整的图模型"""
        logger.info("=" * 80)
        logger.info("开始初始化 Neo4j 知识图谱 Schema（v3.0 - 数据资产优先架构）")
        logger.info(f"向量维度: {self.vector_dimension}")
        logger.info("=" * 80)

        # 1. 创建唯一性约束
        self._create_constraints()

        # 2. 创建普通索引
        self._create_indexes()

        # 3. 创建向量索引
        self._create_vector_indexes()

        # 4. 创建全文索引
        self._create_fulltext_indexes()

        logger.info("\n" + "=" * 80)
        logger.info("✅ Neo4j 知识图谱 Schema 初始化完成")
        logger.info("=" * 80)

    def _create_constraints(self):
        """创建唯一性约束"""
        logger.info("\n步骤 1/4: 创建唯一性约束...")

        constraints = [
            # Domain
            ("Domain", "name", "CREATE CONSTRAINT domain_name_unique IF NOT EXISTS FOR (d:Domain) REQUIRE d.name IS UNIQUE"),

            # Schema
            ("Schema", "layer", "CREATE CONSTRAINT schema_layer_unique IF NOT EXISTS FOR (s:Schema) REQUIRE s.layer IS UNIQUE"),

            # Metrics
            ("AtomicMetric", "name", "CREATE CONSTRAINT atomic_metric_name_unique IF NOT EXISTS FOR (m:AtomicMetric) REQUIRE m.name IS UNIQUE"),
            ("DerivedMetric", "name", "CREATE CONSTRAINT derived_metric_name_unique IF NOT EXISTS FOR (m:DerivedMetric) REQUIRE m.name IS UNIQUE"),
            ("CompositeMetric", "name", "CREATE CONSTRAINT composite_metric_name_unique IF NOT EXISTS FOR (m:CompositeMetric) REQUIRE m.name IS UNIQUE"),

            # QualityRule
            ("QualityRule", "name", "CREATE CONSTRAINT quality_rule_name_unique IF NOT EXISTS FOR (q:QualityRule) REQUIRE q.name IS UNIQUE"),
        ]

        with self.driver.session() as session:
            for label, field, constraint_query in constraints:
                try:
                    session.run(constraint_query)
                    logger.info(f"  ✓ {label}.{field} 唯一性约束创建成功")
                except Exception as e:
                    logger.warning(f"  ⚠ {label}.{field} 约束可能已存在: {e}")

        logger.info(f"  共创建 {len(constraints)} 个唯一性约束")

    def _create_indexes(self):
        """创建普通索引（精确查询用）"""
        logger.info("\n步骤 2/4: 创建普通索引...")

        indexes = [
            # Domain
            ("Domain", "name", "CREATE INDEX domain_name_idx IF NOT EXISTS FOR (d:Domain) ON (d.name)"),

            # Catalog
            ("Catalog", "name", "CREATE INDEX catalog_name_idx IF NOT EXISTS FOR (c:Catalog) ON (c.name)"),

            # Subject
            ("Subject", "name", "CREATE INDEX subject_name_idx IF NOT EXISTS FOR (s:Subject) ON (s.name)"),

            # Schema
            ("Schema", "layer", "CREATE INDEX schema_layer_idx IF NOT EXISTS FOR (s:Schema) ON (s.layer)"),
            ("Schema", "name", "CREATE INDEX schema_name_idx IF NOT EXISTS FOR (s:Schema) ON (s.name)"),

            # Table
            ("Table", "name", "CREATE INDEX table_name_idx IF NOT EXISTS FOR (t:Table) ON (t.name)"),
            ("Table", "displayName", "CREATE INDEX table_displayname_idx IF NOT EXISTS FOR (t:Table) ON (t.displayName)"),

            # Column
            ("Column", "name", "CREATE INDEX column_name_idx IF NOT EXISTS FOR (c:Column) ON (c.name)"),
            ("Column", "dataType", "CREATE INDEX column_datatype_idx IF NOT EXISTS FOR (c:Column) ON (c.dataType)"),

            # AtomicMetric
            ("AtomicMetric", "category", "CREATE INDEX atomic_metric_category_idx IF NOT EXISTS FOR (m:AtomicMetric) ON (m.category)"),

            # DerivedMetric
            ("DerivedMetric", "category", "CREATE INDEX derived_metric_category_idx IF NOT EXISTS FOR (m:DerivedMetric) ON (m.category)"),

            # CompositeMetric
            ("CompositeMetric", "category", "CREATE INDEX composite_metric_category_idx IF NOT EXISTS FOR (m:CompositeMetric) ON (m.category)"),
            ("CompositeMetric", "businessImportance", "CREATE INDEX composite_metric_importance_idx IF NOT EXISTS FOR (m:CompositeMetric) ON (m.businessImportance)"),

            # QualityRule
            ("QualityRule", "ruleType", "CREATE INDEX quality_rule_type_idx IF NOT EXISTS FOR (q:QualityRule) ON (q.ruleType)"),
            ("QualityRule", "isRequired", "CREATE INDEX quality_rule_required_idx IF NOT EXISTS FOR (q:QualityRule) ON (q.isRequired)"),
            ("QualityRule", "isEnabled", "CREATE INDEX quality_rule_enabled_idx IF NOT EXISTS FOR (q:QualityRule) ON (q.isEnabled)"),
        ]

        with self.driver.session() as session:
            for label, field, index_query in indexes:
                try:
                    session.run(index_query)
                    logger.debug(f"  ✓ {label}.{field} 索引创建成功")
                except Exception as e:
                    logger.warning(f"  ⚠ {label}.{field} 索引可能已存在: {e}")

        logger.info(f"  共创建 {len(indexes)} 个普通索引")

    def _create_vector_indexes(self):
        """创建向量索引（语义检索核心）"""
        logger.info("\n步骤 3/4: 创建统一向量索引（基于 Knowledge 标签）...")
        logger.info(f"  使用向量维度: {self.vector_dimension}")

        # 统一向量索引（基于 Knowledge 标签，覆盖所有节点类型）
        unified_vector_index_query = (
            f"CREATE VECTOR INDEX kg_unified_vector_index IF NOT EXISTS "
            f"FOR (n:Knowledge) "
            f"ON (n.embedding) "
            f"OPTIONS {{indexConfig: {{`vector.dimensions`: {self.vector_dimension}, `vector.similarity_function`: 'cosine'}}}}"
        )

        with self.driver.session() as session:
            try:
                session.run(unified_vector_index_query)
                logger.info(f"  ✓ 统一向量索引创建成功（kg_unified_vector_index）")
                logger.info(f"     基于标签: Knowledge（所有知识图谱节点）")
            except Exception as e:
                if "Enterprise" in str(e) or "edition" in str(e):
                    logger.warning(f"  ⚠ 统一向量索引创建失败（需要 Neo4j Enterprise 版本）")
                else:
                    logger.warning(f"  ⚠ 统一向量索引创建失败: {e}")

    def _create_fulltext_indexes(self):
        """创建全文索引（混合检索用）"""
        logger.info("\n步骤 4/4: 创建统一全文索引（基于 Knowledge 标签）...")

        # 统一全文索引（基于 Knowledge 标签，覆盖所有节点类型）
        index_name = "kg_unified_fulltext_index"
        label = "Knowledge"
        properties = ["name", "displayName", "description"]

        try:
            create_fulltext_index(
                self.driver,
                index_name,
                label=label,
                node_properties=properties
            )
            logger.info(f"  ✓ 统一全文索引创建成功（{index_name}）")
            logger.info(f"     基于标签: Knowledge（所有知识图谱节点）")
            logger.info(f"     索引字段: {', '.join(properties)}")
        except Exception as e:
            logger.warning(f"  ⚠ 统一全文索引创建失败: {e}")


def main():
    """主函数"""
    logger.info("🚀 Neo4j 知识图谱 Schema 初始化工具 v3.0")

    # 获取向量维度
    vector_dimension = get_embedding_dimension()

    # 连接 Neo4j
    logger.info(f"连接Neo4j数据库: {NEO4J_URI}")
    driver = GraphDatabase.driver(
        NEO4J_URI,
        auth=(NEO4J_USERNAME, NEO4J_PASSWORD),
    )

    try:
        # 测试连接
        driver.verify_connectivity()
        logger.info("Neo4j连接成功")

        # 初始化图模型
        initializer = KnowledgeGraphSchemaInitializer(driver, vector_dimension)
        initializer.initialize()

        logger.info("\n下一步:")
        logger.info("  1. 运行 python scripts/init_kg_data.py 创建测试数据")
        logger.info("  2. 运行 python scripts/generate_embeddings.py 生成向量嵌入")

    except Exception as e:
        logger.error(f"初始化失败: {e}")
        raise
    finally:
        driver.close()
        logger.info("Neo4j连接已关闭")


if __name__ == "__main__":
    main()
