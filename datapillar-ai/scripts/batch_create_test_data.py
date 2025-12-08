"""
批量生成 Neo4j 测试数据脚本

目标：
- 生成 100+ 张表
- 1000+ 个字段
- 完整的血缘关系（表级 + 列级）
- 质量规则
- 指标节点
- 测试检索效率
"""

import os
import sys
import random
from datetime import datetime

# 添加项目根目录到 Python 路径
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, project_root)

import logging
logger = logging.getLogger(__name__)
from neo4j import GraphDatabase
from src.config import model_manager


# Neo4j 连接配置
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")


class BatchTestDataGenerator:
    """批量测试数据生成器"""

    def __init__(self, driver):
        self.driver = driver

        # 业务域配置
        self.domains = [
            {"name": "trade", "displayName": "交易履约域", "desc": "订单、支付、物流等交易履约业务"},
            {"name": "user", "displayName": "用户增长域", "desc": "用户注册、登录、画像等用户增长业务"},
            {"name": "product", "displayName": "商品运营域", "desc": "商品、类目、库存等商品运营业务"},
            {"name": "marketing", "displayName": "营销推广域", "desc": "活动、优惠券、广告等营销推广业务"},
            {"name": "finance", "displayName": "财务结算域", "desc": "账单、发票、结算等财务结算业务"},
        ]

        # 业务主题配置（每个域下的主题）
        self.subjects_per_domain = {
            "trade": ["订单管理", "支付管理", "物流管理", "退款管理"],
            "user": ["用户注册", "用户画像", "用户行为", "会员管理"],
            "product": ["商品管理", "类目管理", "库存管理", "价格管理"],
            "marketing": ["活动管理", "优惠券管理", "广告投放", "营销效果"],
            "finance": ["账单管理", "发票管理", "结算管理", "对账管理"],
        }

        # 层级配置
        self.layers = ["SRC", "ODS", "DWD", "DWS", "ADS"]

        # 表名前缀（根据业务域和层级）
        self.table_prefixes = {
            "trade": ["order", "payment", "logistics", "refund"],
            "user": ["user", "member", "behavior", "profile"],
            "product": ["product", "category", "inventory", "price"],
            "marketing": ["campaign", "coupon", "ad", "promotion"],
            "finance": ["bill", "invoice", "settlement", "reconciliation"],
        }

        # 常用字段模板（可复用）
        self.common_columns = {
            "id_fields": [
                {"name": "id", "displayName": "主键ID", "type": "BIGINT", "desc": "主键标识"},
                {"name": "order_id", "displayName": "订单ID", "type": "BIGINT", "desc": "订单唯一标识"},
                {"name": "user_id", "displayName": "用户ID", "type": "BIGINT", "desc": "用户唯一标识"},
                {"name": "product_id", "displayName": "商品ID", "type": "BIGINT", "desc": "商品唯一标识"},
            ],
            "amount_fields": [
                {"name": "amount", "displayName": "金额", "type": "DECIMAL(15,2)", "desc": "金额（元）"},
                {"name": "total_amount", "displayName": "总金额", "type": "DECIMAL(15,2)", "desc": "总金额（元）"},
                {"name": "discount_amount", "displayName": "优惠金额", "type": "DECIMAL(15,2)", "desc": "优惠金额（元）"},
                {"name": "real_amount", "displayName": "实付金额", "type": "DECIMAL(15,2)", "desc": "实付金额（元）"},
            ],
            "time_fields": [
                {"name": "created_at", "displayName": "创建时间", "type": "TIMESTAMP", "desc": "记录创建时间"},
                {"name": "updated_at", "displayName": "更新时间", "type": "TIMESTAMP", "desc": "记录更新时间"},
                {"name": "deleted_at", "displayName": "删除时间", "type": "TIMESTAMP", "desc": "记录删除时间（软删除）"},
            ],
            "status_fields": [
                {"name": "status", "displayName": "状态", "type": "VARCHAR(20)", "desc": "记录状态"},
                {"name": "is_deleted", "displayName": "是否删除", "type": "TINYINT", "desc": "0-未删除，1-已删除"},
            ],
            "dimension_fields": [
                {"name": "stat_date", "displayName": "统计日期", "type": "DATE", "desc": "统计日期（分区字段）"},
                {"name": "province", "displayName": "省份", "type": "VARCHAR(50)", "desc": "省份名称"},
                {"name": "city", "displayName": "城市", "type": "VARCHAR(50)", "desc": "城市名称"},
                {"name": "channel", "displayName": "渠道", "type": "VARCHAR(50)", "desc": "来源渠道"},
            ],
        }

    def generate(self, num_tables_per_layer: int = 5):
        """
        批量生成测试数据

        Args:
            num_tables_per_layer: 每个层级每个域生成多少张表（5个域 * 5个层级 * 5张表 = 125张表）
        """
        logger.info("=" * 80)
        logger.info("开始批量生成 Neo4j 测试数据")
        logger.info(f"配置: {len(self.domains)} 个业务域 × {len(self.layers)} 个层级 × {num_tables_per_layer} 张表/层")
        total_tables = len(self.domains) * len(self.layers) * num_tables_per_layer
        logger.info(f"预计生成: {total_tables} 张表")
        logger.info("=" * 80)

        with self.driver.session() as session:
            # 步骤1: 创建业务域
            self._create_domains(session)

            # 步骤2: 创建层级节点
            self._create_schemas(session)

            # 步骤3: 批量创建表和列
            all_tables = self._create_tables_and_columns(session, num_tables_per_layer)

            # 步骤4: 创建表级血缘关系
            self._create_table_lineage(session, all_tables)

            # 步骤5: 创建列级血缘关系
            self._create_column_lineage(session, all_tables)

            # 步骤6: 创建质量规则
            self._create_quality_rules(session, all_tables)

            # 步骤7: 创建指标
            self._create_metrics(session, all_tables)

            # 步骤8: 生成向量（Embedding）
            self._generate_embeddings(session)

        logger.info("\n" + "=" * 80)
        logger.info("✅ 批量测试数据生成完成")
        logger.info("=" * 80)

    def _create_domains(self, session):
        """创建业务域"""
        logger.info("\n步骤 1/8: 创建业务域...")
        for domain in self.domains:
            session.run("""
                MERGE (d:Domain {name: $name})
                SET d.displayName = $displayName,
                    d.description = $desc,
                    d.createdBy = 'BATCH_SCRIPT',
                    d.generatedAt = datetime(),
                    d.confidence = 1.0,
                    d.embedding = []
            """, name=domain["name"], displayName=domain["displayName"], desc=domain["desc"])
            logger.info(f"  ✓ 创建 Domain: {domain['name']} ({domain['displayName']})")

    def _create_schemas(self, session):
        """创建层级节点"""
        logger.info("\n步骤 2/8: 创建数仓分层...")
        layer_descriptions = {
            "SRC": "业务源数据层",
            "ODS": "操作数据层（原始数据）",
            "DWD": "明细数据层（清洗后）",
            "DWS": "汇总数据层（宽表）",
            "ADS": "应用数据层（报表）",
        }
        for layer in self.layers:
            session.run("""
                MERGE (s:Schema {layer: $layer})
                SET s.name = $layer,
                    s.displayName = $displayName,
                    s.description = $desc,
                    s.createdBy = 'BATCH_SCRIPT',
                    s.generatedAt = datetime(),
                    s.confidence = 1.0,
                    s.embedding = []
            """, layer=layer, displayName=layer_descriptions[layer], desc=layer_descriptions[layer])
            logger.info(f"  ✓ 创建 Schema: {layer}")

    def _create_tables_and_columns(self, session, num_tables_per_layer):
        """批量创建表和列"""
        logger.info(f"\n步骤 3/8: 批量创建表和列 ({num_tables_per_layer} 张表/层/域)...")

        all_tables = []
        table_count = 0

        for domain in self.domains:
            domain_name = domain["name"]
            prefixes = self.table_prefixes[domain_name]

            for layer in self.layers:
                for i in range(num_tables_per_layer):
                    # 生成表名
                    prefix = random.choice(prefixes)
                    suffix = ["detail", "summary", "daily", "hourly", "fact", "dim", "snapshot", "log"][i % 8]
                    table_name = f"{layer.lower()}_{prefix}_{suffix}_{i+1}"

                    # 生成显示名
                    display_name = f"{layer}层-{prefix.capitalize()}{suffix.capitalize()}表-{i+1}"

                    # 生成描述
                    description = f"{domain['displayName']} - {layer}层 - {prefix}业务数据"

                    # 创建表节点
                    session.run("""
                        CREATE (t:Table {
                            name: $name,
                            displayName: $displayName,
                            description: $description,
                            layer: $layer,
                            domain: $domain,
                            certificationLevel: $certLevel,
                            qualityScore: $qualityScore,
                            businessValue: $businessValue,
                            tags: $tags,
                            createdBy: 'BATCH_SCRIPT',
                            generatedAt: datetime(),
                            confidence: 1.0,
                            embedding: []
                        })
                    """,
                    name=table_name,
                    displayName=display_name,
                    description=description,
                    layer=layer,
                    domain=domain_name,
                    certLevel=random.choice(["NONE", "CERTIFIED", "VERIFIED"]),
                    qualityScore=random.randint(85, 100),
                    businessValue=f"{prefix}业务的核心数据表",
                    tags=[domain_name, prefix, layer]
                    )

                    # 为表创建列（每张表8-15个字段）
                    num_columns = random.randint(8, 15)
                    columns = self._generate_columns_for_table(table_name, num_columns)

                    for col in columns:
                        session.run("""
                            MATCH (t:Table {name: $table_name})
                            CREATE (c:Column {
                                name: $name,
                                displayName: $displayName,
                                dataType: $dataType,
                                description: $description,
                                isPrimaryKey: $isPrimaryKey,
                                isNullable: $isNullable,
                                createdBy: 'BATCH_SCRIPT',
                                embedding: []
                            })
                            CREATE (t)-[:HAS_COLUMN]->(c)
                        """,
                        table_name=table_name,
                        name=col["name"],
                        displayName=col["displayName"],
                        dataType=col["type"],
                        description=col["desc"],
                        isPrimaryKey=col.get("isPK", False),
                        isNullable=col.get("nullable", True)
                        )

                    all_tables.append({
                        "name": table_name,
                        "layer": layer,
                        "domain": domain_name,
                        "prefix": prefix,
                        "columns": columns
                    })

                    table_count += 1

                    if table_count % 10 == 0:
                        logger.info(f"  已创建 {table_count} 张表...")

        logger.info(f"  ✓ 完成：共创建 {table_count} 张表，约 {table_count * 10} 个字段")
        return all_tables

    def _generate_columns_for_table(self, table_name, num_columns):
        """为表生成字段列表"""
        columns = []

        # 添加主键ID（必有）
        columns.append({
            "name": "id",
            "displayName": "主键ID",
            "type": "BIGINT",
            "desc": f"{table_name}的主键标识",
            "isPK": True,
            "nullable": False
        })

        # 随机添加业务字段
        all_common_fields = []
        for field_list in self.common_columns.values():
            all_common_fields.extend(field_list)

        # 去重（避免重复添加 id）
        selected_fields = random.sample(
            [f for f in all_common_fields if f["name"] != "id"],
            min(num_columns - 1, len(all_common_fields) - 1)
        )

        for field in selected_fields:
            columns.append({
                "name": field["name"],
                "displayName": field["displayName"],
                "type": field["type"],
                "desc": field["desc"],
                "nullable": True
            })

        return columns

    def _create_table_lineage(self, session, all_tables):
        """创建表级血缘关系（按层级顺序）"""
        logger.info("\n步骤 4/8: 创建表级血缘关系...")

        layer_order = ["SRC", "ODS", "DWD", "DWS", "ADS"]
        lineage_count = 0

        # 按业务域分组
        tables_by_domain = {}
        for table in all_tables:
            domain = table["domain"]
            if domain not in tables_by_domain:
                tables_by_domain[domain] = {layer: [] for layer in layer_order}
            tables_by_domain[domain][table["layer"]].append(table)

        # 为每个域创建血缘链路：SRC → ODS → DWD → DWS → ADS
        for domain, layers_dict in tables_by_domain.items():
            for i in range(len(layer_order) - 1):
                source_layer = layer_order[i]
                target_layer = layer_order[i + 1]

                source_tables = layers_dict[source_layer]
                target_tables = layers_dict[target_layer]

                if not source_tables or not target_tables:
                    continue

                # 每个目标表随机选择1-3个上游表
                for target_table in target_tables:
                    num_sources = min(random.randint(1, 3), len(source_tables))
                    selected_sources = random.sample(source_tables, num_sources)

                    for source_table in selected_sources:
                        # 创建表级血缘关系
                        session.run("""
                            MATCH (source:Table {name: $source_name})
                            MATCH (target:Table {name: $target_name})
                            MERGE (target)-[r:DERIVED_FROM]->(source)
                            SET r.transformationType = $transform_type,
                                r.createdBy = 'BATCH_SCRIPT',
                                r.createdAt = datetime()
                        """,
                        source_name=source_table["name"],
                        target_name=target_table["name"],
                        transform_type=self._get_transform_type(source_layer, target_layer)
                        )

                        lineage_count += 1

        logger.info(f"  ✓ 完成：共创建 {lineage_count} 条表级血缘关系")

    def _create_column_lineage(self, session, all_tables):
        """创建列级血缘关系"""
        logger.info("\n步骤 5/8: 创建列级血缘关系...")

        lineage_count = 0

        # 获取所有表级血缘关系
        result = session.run("""
            MATCH (target:Table)-[:DERIVED_FROM]->(source:Table)
            RETURN target.name AS target_table, source.name AS source_table
        """)

        for record in result:
            target_table_name = record["target_table"]
            source_table_name = record["source_table"]

            # 创建列级血缘：匹配相同名称的字段
            session.run("""
                MATCH (source_table:Table {name: $source_table})-[:HAS_COLUMN]->(sc:Column)
                MATCH (target_table:Table {name: $target_table})-[:HAS_COLUMN]->(tc:Column)
                WHERE sc.name = tc.name  // 字段名相同，表示派生关系
                MERGE (tc)-[r:DERIVED_FROM]->(sc)
                SET r.transformationType = 'DIRECT',
                    r.createdBy = 'BATCH_SCRIPT',
                    r.createdAt = datetime()
            """,
            source_table=source_table_name,
            target_table=target_table_name
            )

            lineage_count += 1

        logger.info(f"  ✓ 完成：共创建列级血缘关系（基于字段名匹配）")

    def _create_quality_rules(self, session, all_tables):
        """创建质量规则"""
        logger.info("\n步骤 6/8: 创建质量规则...")

        rule_templates = [
            {"type": "NOT_NULL", "desc": "非空校验", "severity": "HIGH"},
            {"type": "UNIQUE", "desc": "唯一性校验", "severity": "HIGH"},
            {"type": "RANGE", "desc": "范围校验", "severity": "MEDIUM"},
            {"type": "FORMAT", "desc": "格式校验", "severity": "MEDIUM"},
            {"type": "ENUM", "desc": "枚举值校验", "severity": "LOW"},
        ]

        rule_count = 0

        # 为每张表的部分字段创建质量规则
        for table in random.sample(all_tables, min(50, len(all_tables))):  # 随机选择50张表
            table_name = table["name"]

            # 为表的部分字段创建规则
            result = session.run("""
                MATCH (t:Table {name: $table_name})-[:HAS_COLUMN]->(c:Column)
                RETURN c.name AS column_name
                LIMIT 3
            """, table_name=table_name)

            for record in result:
                column_name = record["column_name"]
                rule_template = random.choice(rule_templates)

                rule_name = f"{table_name}.{column_name}_{rule_template['type']}"

                session.run("""
                    MATCH (t:Table {name: $table_name})-[:HAS_COLUMN]->(c:Column {name: $column_name})
                    CREATE (qr:QualityRule {
                        ruleId: $rule_id,
                        ruleName: $rule_name,
                        ruleType: $rule_type,
                        description: $description,
                        severity: $severity,
                        isEnabled: true,
                        createdBy: 'BATCH_SCRIPT',
                        embedding: []
                    })
                    CREATE (c)-[:HAS_QUALITY_RULE]->(qr)
                """,
                table_name=table_name,
                column_name=column_name,
                rule_id=rule_name,
                rule_name=rule_name,
                rule_type=rule_template["type"],
                description=rule_template["desc"],
                severity=rule_template["severity"]
                )

                rule_count += 1

        logger.info(f"  ✓ 完成：共创建 {rule_count} 条质量规则")

    def _create_metrics(self, session, all_tables):
        """创建指标节点"""
        logger.info("\n步骤 7/8: 创建指标节点...")

        metric_templates = [
            {"type": "AtomicMetric", "suffix": "总数", "formula": "COUNT(*)"},
            {"type": "AtomicMetric", "suffix": "总金额", "formula": "SUM(amount)"},
            {"type": "DerivedMetric", "suffix": "近7天总数", "formula": "COUNT(*) WHERE date >= CURRENT_DATE - 7"},
            {"type": "DerivedMetric", "suffix": "近30天GMV", "formula": "SUM(amount) WHERE date >= CURRENT_DATE - 30"},
            {"type": "CompositeMetric", "suffix": "平均值", "formula": "SUM(amount) / COUNT(*)"},
        ]

        metric_count = 0

        # 为部分 DWS/ADS 层的表创建指标
        for table in all_tables:
            if table["layer"] not in ["DWS", "ADS"]:
                continue

            # 为每张表创建1-3个指标（使用表名确保唯一性）
            num_metrics = random.randint(1, 3)
            for i in range(num_metrics):
                metric_template = random.choice(metric_templates)
                metric_name = f"{table['name']}_{metric_template['suffix'].lower().replace(' ', '_')}_{i+1}"

                session.run("""
                    MATCH (t:Table {name: $table_name})
                    CREATE (m:""" + metric_template["type"] + """ {
                        name: $metric_name,
                        displayName: $display_name,
                        description: $description,
                        formula: $formula,
                        unit: $unit,
                        createdBy: 'BATCH_SCRIPT',
                        embedding: []
                    })
                    CREATE (m)-[:RELATED_TO_TABLE]->(t)
                """,
                table_name=table["name"],
                metric_name=metric_name,
                display_name=f"{table['prefix'].capitalize()}{metric_template['suffix']}",
                description=f"{table['prefix']}相关的{metric_template['suffix']}指标",
                formula=metric_template["formula"],
                unit="个" if "总数" in metric_template["suffix"] else "元"
                )

                metric_count += 1

        logger.info(f"  ✓ 完成：共创建 {metric_count} 个指标")

    def _generate_embeddings(self, session):
        """生成向量（Embedding）"""
        logger.info("\n步骤 8/8: 生成向量（Embedding）...")

        # 获取 Embedding 模型配置
        model_config = model_manager.get_default_embedding_model()
        if not model_config:
            logger.warning("  ⚠️  未找到默认 Embedding 模型，跳过向量生成")
            return

        logger.info(f"  使用模型: {model_config.provider} - {model_config.model_name}")

        # 获取所有需要生成向量的节点
        node_types = ["Table", "Column", "Domain", "Schema", "AtomicMetric", "DerivedMetric", "CompositeMetric", "QualityRule"]

        from src.graphrag.graphrag import embed

        total_count = 0
        for node_type in node_types:
            result = session.run(f"""
                MATCH (n:{node_type})
                WHERE size(n.embedding) = 0 OR n.embedding IS NULL
                RETURN elementId(n) AS id, n.name AS name, n.displayName AS displayName, n.description AS description
                LIMIT 200
            """)

            count = 0
            for record in result:
                node_id = record["id"]
                name = record["name"] or ""
                display_name = record["displayName"] or ""
                description = record["description"] or ""

                # 组合文本用于生成向量
                text = f"{display_name} {name} {description}".strip()

                if not text:
                    continue

                try:
                    # 生成向量
                    vector = embed(text)

                    # 更新节点
                    session.run(f"""
                        MATCH (n:{node_type})
                        WHERE elementId(n) = $id
                        SET n.embedding = $vector
                    """, id=node_id, vector=vector)

                    count += 1
                    total_count += 1

                    if count % 20 == 0:
                        logger.info(f"  {node_type}: 已生成 {count} 个向量...")

                except Exception as e:
                    logger.warning(f"  生成向量失败: {e}")

            if count > 0:
                logger.info(f"  ✓ {node_type}: 完成 {count} 个向量")

        logger.info(f"  ✓ 完成：共生成 {total_count} 个向量")

    def _get_transform_type(self, source_layer, target_layer):
        """根据层级获取转换类型"""
        if source_layer == "SRC" and target_layer == "ODS":
            return "SYNC"
        elif source_layer == "ODS" and target_layer == "DWD":
            return "CLEAN"
        elif source_layer == "DWD" and target_layer == "DWS":
            return "AGGREGATE"
        elif source_layer == "DWS" and target_layer == "ADS":
            return "REPORT"
        else:
            return "TRANSFORM"


def main():
    """主函数"""
    logger.info("连接 Neo4j...")
    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))

    try:
        generator = BatchTestDataGenerator(driver)

        # 生成数据（5个域 × 5个层级 × 5张表 = 125张表）
        generator.generate(num_tables_per_layer=5)

        logger.info("\n" + "=" * 80)
        logger.info("数据统计:")
        logger.info("=" * 80)

        with driver.session() as session:
            # 统计节点数量
            stats = [
                ("Domain", "业务域"),
                ("Schema", "数仓分层"),
                ("Table", "表"),
                ("Column", "字段"),
                ("QualityRule", "质量规则"),
                ("AtomicMetric", "原子指标"),
                ("DerivedMetric", "派生指标"),
                ("CompositeMetric", "复合指标"),
            ]

            for node_type, label in stats:
                result = session.run(f"MATCH (n:{node_type}) RETURN count(n) AS count")
                count = result.single()["count"]
                logger.info(f"  {label:12s}: {count:6d} 个")

            # 统计关系数量
            logger.info("\n关系统计:")
            relations = [
                ("HAS_COLUMN", "表-列关系"),
                ("DERIVED_FROM", "血缘关系"),
                ("HAS_QUALITY_RULE", "质量规则关系"),
                ("RELATED_TO_TABLE", "指标-表关系"),
            ]

            for rel_type, label in relations:
                result = session.run(f"MATCH ()-[r:{rel_type}]->() RETURN count(r) AS count")
                count = result.single()["count"]
                logger.info(f"  {label:16s}: {count:6d} 条")

    finally:
        driver.close()
        logger.info("\n✅ 完成！")


if __name__ == "__main__":
    main()
