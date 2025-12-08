"""
Neo4j 知识图谱示例数据初始化脚本
根据 docs/knowledge-graph-design-v3.md 创建测试数据
版本：v3.0 - 以数据资产为中心的业务化知识图谱
"""

import os
from datetime import datetime

import logging
logger = logging.getLogger(__name__)
from neo4j import GraphDatabase


# 直接从环境变量读取配置
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")


class KnowledgeGraphDataInitializer:
    """知识图谱示例数据初始化器 - v3.0"""

    def __init__(self, driver):
        self.driver = driver

    def initialize(self):
        """初始化示例数据"""
        logger.info("=" * 80)
        logger.info("开始创建 Neo4j 知识图谱示例数据（v3.0）")
        logger.info("=" * 80)

        # 1. 创建层次结构节点
        self._create_hierarchy_nodes()

        # 2. 创建层次结构关系
        self._create_hierarchy_relationships()

        # 3. 创建表血缘关系
        self._create_table_lineage_relationships()

        # 4. 创建列血缘关系
        self._create_column_lineage_relationships()

        # 5. 创建 Join 节点和关系
        self._create_join_nodes_and_relationships()

        # 6. 创建指标节点
        self._create_metric_nodes()

        # 7. 创建指标关系
        self._create_metric_relationships()

        # 8. 创建质量规则节点
        self._create_quality_rule_nodes()

        # 9. 创建质量规则关系
        self._create_quality_rule_relationships()

        logger.info("\n" + "=" * 80)
        logger.info("✅ Neo4j 知识图谱示例数据创建完成")
        logger.info("=" * 80)

    def _create_hierarchy_nodes(self):
        """创建层次结构节点"""
        logger.info("\n步骤 1/7: 创建层次结构节点...")

        with self.driver.session() as session:
            # 创建 Domain（业务域）
            session.run("""
                CREATE (d:Domain:Knowledge {
                    name: 'trade_domain',
                    displayName: '交易履约业务域',
                    description: '交易域包含订单系统和支付系统，核心主题为订单管理和支付管理，支撑电商平台的交易履约全流程',
                    businessGoals: ['提升GMV', '降低退款率', '提高支付成功率'],
                    embedding: [],
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 Domain: trade_domain")

            # 创建 Catalog（数据目录）
            session.run("""
                CREATE (c:Catalog:Knowledge {
                    name: 'order_catalog',
                    displayName: '订单数据',
                    description: '包含订单全生命周期的业务数据，支持订单分析、订单管理、订单报表等业务场景',
                    dataScope: '2020年至今的所有订单数据',
                    tags: ['订单', '核心业务', '交易'],
                    embedding: [],
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 Catalog: order_catalog")

            # 创建 Subject（业务主题）
            session.run("""
                CREATE (s:Subject:Knowledge {
                    name: 'order_management',
                    displayName: '订单管理',
                    description: '订单创建、修改、取消等全生命周期管理',
                    tags: ['订单', '核心业务'],
                    embedding: [],
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 Subject: order_management")

            # 创建 Schema（数仓分层）
            schemas = [
                ("SRC", "业务源数据层", "来自业务系统的源表，未同步到数仓"),
                ("ODS", "操作数据层", "原始数据，未经加工，保留业务系统的原始状态"),
                ("DWD", "明细数据层", "清洗后的明细数据，保持业务含义不变"),
                ("DWS", "汇总数据层", "按主题汇总的宽表数据，支持多维分析"),
            ]
            for layer, display_name, desc in schemas:
                session.run("""
                    CREATE (s:Schema:Knowledge {
                        layer: $layer,
                        name: $layer,
                        displayName: $displayName,
                        description: $description,
                        embedding: [],
                        createdBy: 'MANUAL',
                        generatedAt: datetime(),
                        confidence: 1.0
                    })
                """, layer=layer, displayName=display_name, description=desc)
                logger.info(f"  ✓ 创建 Schema: {layer}")

            # 创建 Table（表）
            tables = [
                # SRC 层：业务源表
                {
                    "name": "mysql_order",
                    "displayName": "订单业务表（MySQL）",
                    "description": "订单业务数据表",
                    "schema": "SRC",
                    "sampleData": '{"order_id": "20231124001", "user_id": 1001, "product_id": 5001, "amount": 199.99}',
                    "businessValue": "业务系统原始订单数据",
                    "qualityScore": 90,
                    "certificationLevel": "NONE",
                },
                {
                    "name": "mysql_user",
                    "displayName": "用户业务表（MySQL）",
                    "description": "用户业务数据表",
                    "schema": "SRC",
                    "sampleData": '{"user_id": 1001, "username": "zhangsan", "mobile": "13812341234", "register_time": "2023-01-15"}',
                    "businessValue": "业务系统原始用户数据",
                    "qualityScore": 90,
                    "certificationLevel": "NONE",
                },
                {
                    "name": "mysql_product",
                    "displayName": "商品业务表（MySQL）",
                    "description": "商品业务数据表",
                    "schema": "SRC",
                    "sampleData": '{"product_id": 5001, "product_name": "iPhone 15 Pro", "category": "数码电子", "price": 7999.00}',
                    "businessValue": "业务系统原始商品数据",
                    "qualityScore": 90,
                    "certificationLevel": "NONE",
                },
                # ODS 层：操作数据层
                {
                    "name": "ods_order",
                    "displayName": "订单原始表",
                    "description": "订单数据表",
                    "schema": "ODS",
                    "sampleData": '{"order_id": "20231124001", "user_id": 1001, "product_id": 5001, "amount": 199.99}',
                    "businessValue": "核心交易数据，支持订单分析、用户行为分析等场景",
                    "qualityScore": 95,
                    "certificationLevel": "CERTIFIED",
                },
                {
                    "name": "ods_user",
                    "displayName": "用户原始表",
                    "description": "用户数据表",
                    "schema": "ODS",
                    "sampleData": '{"user_id": 1001, "username": "zhangsan", "mobile": "138****1234", "register_time": "2023-01-15"}',
                    "businessValue": "用户维度数据，支持用户画像、用户分析等场景",
                    "qualityScore": 96,
                    "certificationLevel": "CERTIFIED",
                },
                {
                    "name": "ods_product",
                    "displayName": "商品原始表",
                    "description": "商品数据表",
                    "schema": "ODS",
                    "sampleData": '{"product_id": 5001, "product_name": "iPhone 15 Pro", "category": "数码电子", "price": 7999.00}',
                    "businessValue": "商品维度数据，支持商品分析、销售分析等场景",
                    "qualityScore": 97,
                    "certificationLevel": "CERTIFIED",
                },
                {
                    "name": "dwd_order_detail",
                    "displayName": "订单明细表",
                    "description": "订单明细数据表",
                    "schema": "DWD",
                    "sampleData": '{"order_id": "20231124001", "order_date": "2023-11-24", "amount": 199.99}',
                    "businessValue": "支持订单多维分析和报表",
                    "qualityScore": 98,
                    "certificationLevel": "CERTIFIED",
                },
                {
                    "name": "dws_order_summary_daily",
                    "displayName": "订单日汇总表",
                    "description": "订单汇总数据表",
                    "schema": "DWS",
                    "sampleData": '{"stat_date": "2023-11-24", "order_count": 1500, "gmv": 299850.00}',
                    "businessValue": "支持日报、周报等汇总分析",
                    "qualityScore": 99,
                    "certificationLevel": "CERTIFIED",
                },
            ]

            for table in tables:
                session.run("""
                    CREATE (t:Table:Knowledge {
                        name: $name,
                        displayName: $displayName,
                        description: $description,
                        embedding: [],
                        createdBy: 'MANUAL',
                        generatedAt: datetime(),
                        confidence: 1.0,
                        sampleData: $sampleData,
                        businessValue: $businessValue,
                        qualityScore: $qualityScore,
                        certificationLevel: $certificationLevel,
                        tags: ['订单', '核心']
                    })
                """, **table)
                logger.info(f"  ✓ 创建 Table: {table['name']}")

            # 创建 Column（字段）
            columns = [
                # === SRC 层：mysql_order 字段 ===
                {
                    "table": "mysql_order",
                    "name": "order_id",
                    "displayName": "订单ID",
                    "dataType": "BIGINT",
                    "description": "订单唯一标识",
                    "sampleData": ["20231124001", "20231124002", "20231124003"],
                },
                {
                    "table": "mysql_order",
                    "name": "user_id",
                    "displayName": "用户ID",
                    "dataType": "BIGINT",
                    "description": "下单用户ID",
                    "sampleData": ["1001", "1002", "1003"],
                },
                {
                    "table": "mysql_order",
                    "name": "product_id",
                    "displayName": "商品ID",
                    "dataType": "BIGINT",
                    "description": "订单商品ID",
                    "sampleData": ["5001", "5002", "5003"],
                },
                {
                    "table": "mysql_order",
                    "name": "order_amount",
                    "displayName": "订单金额",
                    "dataType": "DECIMAL(10,2)",
                    "description": "订单总金额，单位：元",
                    "sampleData": ["199.99", "299.50", "89.00"],
                },
                {
                    "table": "mysql_order",
                    "name": "order_status",
                    "displayName": "订单状态",
                    "dataType": "VARCHAR(20)",
                    "description": "订单当前状态",
                    "sampleData": ["已支付", "待支付", "已完成"],
                },
                {
                    "table": "mysql_order",
                    "name": "order_time",
                    "displayName": "下单时间",
                    "dataType": "DATETIME",
                    "description": "订单创建时间",
                    "sampleData": ["2023-11-24 10:30:00", "2023-11-24 11:15:00", "2023-11-24 14:20:00"],
                },
                # === SRC 层：mysql_user 字段 ===
                {
                    "table": "mysql_user",
                    "name": "user_id",
                    "displayName": "用户ID",
                    "dataType": "BIGINT",
                    "description": "用户唯一标识",
                    "sampleData": ["1001", "1002", "1003"],
                },
                {
                    "table": "mysql_user",
                    "name": "username",
                    "displayName": "用户名",
                    "dataType": "VARCHAR(50)",
                    "description": "用户登录名",
                    "sampleData": ["zhangsan", "lisi", "wangwu"],
                },
                {
                    "table": "mysql_user",
                    "name": "mobile",
                    "displayName": "手机号",
                    "dataType": "VARCHAR(20)",
                    "description": "用户手机号",
                    "sampleData": ["13812341234", "13987654321", "15012349876"],
                },
                {
                    "table": "mysql_user",
                    "name": "register_time",
                    "displayName": "注册时间",
                    "dataType": "DATETIME",
                    "description": "用户注册时间",
                    "sampleData": ["2023-01-15 08:00:00", "2023-02-20 14:30:00", "2023-03-10 16:45:00"],
                },
                # === SRC 层：mysql_product 字段 ===
                {
                    "table": "mysql_product",
                    "name": "product_id",
                    "displayName": "商品ID",
                    "dataType": "BIGINT",
                    "description": "商品唯一标识",
                    "sampleData": ["5001", "5002", "5003"],
                },
                {
                    "table": "mysql_product",
                    "name": "product_name",
                    "displayName": "商品名称",
                    "dataType": "VARCHAR(200)",
                    "description": "商品完整名称",
                    "sampleData": ["iPhone 15 Pro", "MacBook Pro", "AirPods Pro"],
                },
                {
                    "table": "mysql_product",
                    "name": "category",
                    "displayName": "商品类目",
                    "dataType": "VARCHAR(50)",
                    "description": "商品所属类目",
                    "sampleData": ["数码电子", "电脑办公", "影音娱乐"],
                },
                {
                    "table": "mysql_product",
                    "name": "price",
                    "displayName": "商品价格",
                    "dataType": "DECIMAL(10,2)",
                    "description": "商品售价",
                    "sampleData": ["7999.00", "12999.00", "1999.00"],
                },
                # === ODS 层：ods_order 字段 ===
                {
                    "table": "ods_order",
                    "name": "order_id",
                    "displayName": "订单ID",
                    "dataType": "BIGINT",
                    "description": "订单唯一标识，全局唯一，用于关联其他业务表",
                    "sampleData": ["20231124001", "20231124002", "20231124003"],
                },
                {
                    "table": "ods_order",
                    "name": "user_id",
                    "displayName": "用户ID",
                    "dataType": "BIGINT",
                    "description": "下单用户的唯一标识，关联用户表",
                    "sampleData": ["1001", "1002", "1003"],
                },
                {
                    "table": "ods_order",
                    "name": "product_id",
                    "displayName": "商品ID",
                    "dataType": "BIGINT",
                    "description": "订单商品的唯一标识，关联商品表",
                    "sampleData": ["5001", "5002", "5003"],
                },
                {
                    "table": "ods_order",
                    "name": "order_amount",
                    "displayName": "订单金额",
                    "dataType": "DECIMAL(10,2)",
                    "description": "订单总金额，单位：元",
                    "sampleData": ["199.99", "299.50", "89.00"],
                },
                {
                    "table": "ods_order",
                    "name": "order_status",
                    "displayName": "订单状态",
                    "dataType": "VARCHAR(20)",
                    "description": "订单当前状态：待支付、已支付、已发货、已完成、已取消",
                    "sampleData": ["已支付", "待支付", "已完成"],
                },
                # ods_user 字段
                {
                    "table": "ods_user",
                    "name": "user_id",
                    "displayName": "用户ID",
                    "dataType": "BIGINT",
                    "description": "用户唯一标识，主键",
                    "sampleData": ["1001", "1002", "1003"],
                },
                {
                    "table": "ods_user",
                    "name": "username",
                    "displayName": "用户名",
                    "dataType": "VARCHAR(50)",
                    "description": "用户登录名",
                    "sampleData": ["zhangsan", "lisi", "wangwu"],
                },
                {
                    "table": "ods_user",
                    "name": "mobile",
                    "displayName": "手机号",
                    "dataType": "VARCHAR(20)",
                    "description": "用户手机号，脱敏后数据",
                    "sampleData": ["138****1234", "139****5678", "150****9012"],
                },
                # ods_product 字段
                {
                    "table": "ods_product",
                    "name": "product_id",
                    "displayName": "商品ID",
                    "dataType": "BIGINT",
                    "description": "商品唯一标识，主键",
                    "sampleData": ["5001", "5002", "5003"],
                },
                {
                    "table": "ods_product",
                    "name": "product_name",
                    "displayName": "商品名称",
                    "dataType": "VARCHAR(200)",
                    "description": "商品完整名称",
                    "sampleData": ["iPhone 15 Pro", "MacBook Pro", "AirPods Pro"],
                },
                {
                    "table": "ods_product",
                    "name": "category",
                    "displayName": "商品类目",
                    "dataType": "VARCHAR(50)",
                    "description": "商品所属类目",
                    "sampleData": ["数码电子", "电脑办公", "影音娱乐"],
                },
                # dwd_order_detail 字段（明细层，清洗后的宽表）
                {
                    "table": "dwd_order_detail",
                    "name": "order_id",
                    "displayName": "订单ID",
                    "dataType": "BIGINT",
                    "description": "订单唯一标识",
                    "sampleData": ["20231124001", "20231124002"],
                },
                {
                    "table": "dwd_order_detail",
                    "name": "user_id",
                    "displayName": "用户ID",
                    "dataType": "BIGINT",
                    "description": "下单用户ID",
                    "sampleData": ["1001", "1002"],
                },
                {
                    "table": "dwd_order_detail",
                    "name": "username",
                    "displayName": "用户名",
                    "dataType": "VARCHAR(50)",
                    "description": "用户登录名（关联自 ods_user）",
                    "sampleData": ["zhangsan", "lisi"],
                },
                {
                    "table": "dwd_order_detail",
                    "name": "product_id",
                    "displayName": "商品ID",
                    "dataType": "BIGINT",
                    "description": "订单商品ID",
                    "sampleData": ["5001", "5002"],
                },
                {
                    "table": "dwd_order_detail",
                    "name": "product_name",
                    "displayName": "商品名称",
                    "dataType": "VARCHAR(200)",
                    "description": "商品完整名称（关联自 ods_product）",
                    "sampleData": ["iPhone 15 Pro", "MacBook Pro"],
                },
                {
                    "table": "dwd_order_detail",
                    "name": "order_amount",
                    "displayName": "订单金额",
                    "dataType": "DECIMAL(10,2)",
                    "description": "订单总金额，单位：元",
                    "sampleData": ["199.99", "299.50"],
                },
                {
                    "table": "dwd_order_detail",
                    "name": "order_status",
                    "displayName": "订单状态",
                    "dataType": "VARCHAR(20)",
                    "description": "订单当前状态",
                    "sampleData": ["已支付", "待支付"],
                },
                # dws_order_summary_daily 字段（汇总层，每日统计）
                {
                    "table": "dws_order_summary_daily",
                    "name": "stat_date",
                    "displayName": "统计日期",
                    "dataType": "DATE",
                    "description": "统计日期",
                    "sampleData": ["2023-11-24", "2023-11-23"],
                },
                {
                    "table": "dws_order_summary_daily",
                    "name": "total_orders",
                    "displayName": "订单总数",
                    "dataType": "BIGINT",
                    "description": "当日订单总数",
                    "sampleData": ["1520", "1380"],
                },
                {
                    "table": "dws_order_summary_daily",
                    "name": "total_amount",
                    "displayName": "总金额",
                    "dataType": "DECIMAL(15,2)",
                    "description": "当日订单总金额",
                    "sampleData": ["256789.50", "198456.00"],
                },
                {
                    "table": "dws_order_summary_daily",
                    "name": "total_users",
                    "displayName": "用户数",
                    "dataType": "BIGINT",
                    "description": "当日下单用户数",
                    "sampleData": ["890", "756"],
                },
            ]

            for col in columns:
                # 创建 Column 节点并同时建立与 Table 的 HAS_COLUMN 关系
                session.run("""
                    MATCH (t:Table {name: $table})
                    CREATE (t)-[:HAS_COLUMN {generatedAt: datetime()}]->(c:Column:Knowledge {
                        name: $name,
                        displayName: $displayName,
                        dataType: $dataType,
                        description: $description,
                        embedding: [],
                        createdBy: 'MANUAL',
                        generatedAt: datetime(),
                        confidence: 1.0,
                        sampleData: $sampleData
                    })
                """, **col)
                logger.info(f"  ✓ 创建 Column: {col['table']}.{col['name']}")

        logger.info(f"  共创建: 1个Domain, 1个Catalog, 1个Subject, 4个Schema, 8个Table, 36个Column")

    def _create_hierarchy_relationships(self):
        """创建层次结构关系"""
        logger.info("\n步骤 2/7: 创建层次结构关系...")

        with self.driver.session() as session:
            # Domain -> Catalog
            session.run("""
                MATCH (d:Domain {name: 'trade_domain'})
                MATCH (c:Catalog {name: 'order_catalog'})
                CREATE (d)-[:CONTAINS {generatedAt: datetime()}]->(c)
            """)
            logger.info("  ✓ Domain -[:CONTAINS]-> Catalog")

            # Catalog -> Subject
            session.run("""
                MATCH (c:Catalog {name: 'order_catalog'})
                MATCH (s:Subject {name: 'order_management'})
                CREATE (c)-[:CONTAINS {generatedAt: datetime()}]->(s)
            """)
            logger.info("  ✓ Catalog -[:CONTAINS]-> Subject")

            # Subject -> Schema
            for schema_layer in ["SRC", "ODS", "DWD", "DWS"]:
                session.run("""
                    MATCH (s:Subject {name: 'order_management'})
                    MATCH (sch:Schema {layer: $layer})
                    CREATE (s)-[:CONTAINS {generatedAt: datetime()}]->(sch)
                """, layer=schema_layer)
                logger.info(f"  ✓ Subject -[:CONTAINS]-> Schema({schema_layer})")

            # Schema -> Table
            table_schema_mapping = [
                ("mysql_order", "SRC"),
                ("mysql_user", "SRC"),
                ("mysql_product", "SRC"),
                ("ods_order", "ODS"),
                ("ods_user", "ODS"),
                ("ods_product", "ODS"),
                ("dwd_order_detail", "DWD"),
                ("dws_order_summary_daily", "DWS"),
            ]
            for table_name, schema_layer in table_schema_mapping:
                session.run("""
                    MATCH (sch:Schema {layer: $layer})
                    MATCH (t:Table {name: $tableName})
                    CREATE (sch)-[:CONTAINS {generatedAt: datetime()}]->(t)
                """, layer=schema_layer, tableName=table_name)
                logger.info(f"  ✓ Schema({schema_layer}) -[:CONTAINS]-> Table({table_name})")

            # Table -> Column 关系已在 _create_hierarchy_nodes() 中创建

        logger.info("  共创建: 14个层次关系 (1 Domain->Catalog + 1 Catalog->Subject + 4 Subject->Schema + 8 Schema->Table)")

    def _create_table_lineage_relationships(self):
        """创建表之间的血缘关系（数据加工链路）"""
        logger.info("\n步骤 2.5/7: 创建表血缘关系...")

        with self.driver.session() as session:
            # ODS <- SRC (订单)
            session.run("""
                MATCH (source:Table {name: 'mysql_order'})
                MATCH (target:Table {name: 'ods_order'})
                CREATE (target)-[:DERIVED_FROM {
                    transformationType: 'SYNC',
                    createdBy: 'MANUAL',
                    generatedAt: datetime()
                }]->(source)
            """)
            logger.info("  ✓ Table(ods_order) -[:DERIVED_FROM]-> Table(mysql_order)")

            # DWD <- ODS
            session.run("""
                MATCH (source:Table {name: 'ods_order'})
                MATCH (target:Table {name: 'dwd_order_detail'})
                CREATE (target)-[:DERIVED_FROM {
                    transformationType: 'CLEAN',
                    createdBy: 'MANUAL',
                    generatedAt: datetime()
                }]->(source)
            """)
            logger.info("  ✓ Table(dwd_order_detail) -[:DERIVED_FROM]-> Table(ods_order)")

            # DWS <- DWD
            session.run("""
                MATCH (source:Table {name: 'dwd_order_detail'})
                MATCH (target:Table {name: 'dws_order_summary_daily'})
                CREATE (target)-[:DERIVED_FROM {
                    transformationType: 'AGGREGATE',
                    createdBy: 'MANUAL',
                    generatedAt: datetime()
                }]->(source)
            """)
            logger.info("  ✓ Table(dws_order_summary_daily) -[:DERIVED_FROM]-> Table(dwd_order_detail)")

        logger.info("  共创建: 3个表血缘关系")

    def _create_column_lineage_relationships(self):
        """创建列血缘关系（Column -> Column DERIVED_FROM）"""
        logger.info("\n步骤 4/9: 创建列血缘关系...")

        with self.driver.session() as session:
            # ODS -> DWD 列映射（直接映射）
            # ods_order -> dwd_order_detail
            direct_mappings = [
                ("ods_order", "order_id", "dwd_order_detail", "order_id", "DIRECT"),
                ("ods_order", "user_id", "dwd_order_detail", "user_id", "DIRECT"),
                ("ods_order", "product_id", "dwd_order_detail", "product_id", "DIRECT"),
                ("ods_order", "order_amount", "dwd_order_detail", "order_amount", "DIRECT"),
                ("ods_order", "order_status", "dwd_order_detail", "order_status", "DIRECT"),
                # ods_user -> dwd_order_detail
                ("ods_user", "username", "dwd_order_detail", "username", "DIRECT"),
                # ods_product -> dwd_order_detail
                ("ods_product", "product_name", "dwd_order_detail", "product_name", "DIRECT"),
            ]

            for src_table, src_col, tgt_table, tgt_col, trans_type in direct_mappings:
                session.run("""
                    MATCH (source_col:Column {name: $srcCol})
                         <-[:HAS_COLUMN]-(source_table:Table {name: $srcTable})
                    MATCH (target_col:Column {name: $tgtCol})
                         <-[:HAS_COLUMN]-(target_table:Table {name: $tgtTable})
                    CREATE (target_col)-[:DERIVED_FROM {
                        transformationType: $transType,
                        createdBy: 'MANUAL',
                        generatedAt: datetime()
                    }]->(source_col)
                """, srcTable=src_table, srcCol=src_col, tgtTable=tgt_table, tgtCol=tgt_col, transType=trans_type)
                logger.info(f"  ✓ Column({tgt_table}.{tgt_col}) -[:DERIVED_FROM]-> Column({src_table}.{src_col}) [{trans_type}]")

            # DWD -> DWS 列映射（聚合映射）
            aggregate_mappings = [
                ("dwd_order_detail", "order_id", "dws_order_summary_daily", "total_orders", "AGGREGATE", "COUNT"),
                ("dwd_order_detail", "order_amount", "dws_order_summary_daily", "total_amount", "AGGREGATE", "SUM"),
                ("dwd_order_detail", "user_id", "dws_order_summary_daily", "total_users", "AGGREGATE", "COUNT_DISTINCT"),
            ]

            for src_table, src_col, tgt_table, tgt_col, trans_type, func in aggregate_mappings:
                session.run("""
                    MATCH (source_col:Column {name: $srcCol})
                         <-[:HAS_COLUMN]-(source_table:Table {name: $srcTable})
                    MATCH (target_col:Column {name: $tgtCol})
                         <-[:HAS_COLUMN]-(target_table:Table {name: $tgtTable})
                    CREATE (target_col)-[:DERIVED_FROM {
                        transformationType: $transType,
                        transformationFunction: $func,
                        createdBy: 'MANUAL',
                        generatedAt: datetime()
                    }]->(source_col)
                """, srcTable=src_table, srcCol=src_col, tgtTable=tgt_table, tgtCol=tgt_col, transType=trans_type, func=func)
                logger.info(f"  ✓ Column({tgt_table}.{tgt_col}) -[:DERIVED_FROM]-> Column({src_table}.{src_col}) [{trans_type}:{func}]")

        logger.info("  共创建: 10个列血缘关系（7个直接映射 + 3个聚合映射）")

    def _create_metric_nodes(self):
        """创建指标节点"""
        logger.info("\n步骤 3/7: 创建指标节点...")

        with self.driver.session() as session:
            # 原子指标
            atomic_metrics = [
                {
                    "name": "order_amount",
                    "displayName": "订单金额",
                    "description": "单笔订单的交易金额，不含退款",
                    "metricType": "SUM",
                    "unit": "元",
                    "category": "交易指标",
                },
                {
                    "name": "order_count",
                    "displayName": "订单数",
                    "description": "订单总数量",
                    "metricType": "COUNT",
                    "unit": "个",
                    "category": "交易指标",
                },
            ]

            for metric in atomic_metrics:
                session.run("""
                    CREATE (m:AtomicMetric:Knowledge {
                        name: $name,
                        displayName: $displayName,
                        description: $description,
                        metricType: $metricType,
                        unit: $unit,
                        category: $category,
                        embedding: [],
                        createdBy: 'MANUAL',
                        generatedAt: datetime(),
                        confidence: 1.0
                    })
                """, **metric)
                logger.info(f"  ✓ 创建 AtomicMetric: {metric['name']}")

            # 派生指标
            session.run("""
                CREATE (m:DerivedMetric:Knowledge {
                    name: 'gmv_last_7days',
                    displayName: '近7天GMV',
                    description: '最近7天的订单金额总和',
                    timeModifier: 'LAST_7_DAYS',
                    formula: 'SUM(order_amount) WHERE date >= CURRENT_DATE - 7',
                    category: '交易指标',
                    embedding: [],
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 DerivedMetric: gmv_last_7days")

            # 复合指标
            session.run("""
                CREATE (m:CompositeMetric:Knowledge {
                    name: 'avg_order_value',
                    displayName: '客单价',
                    description: '平均每单金额，用于衡量订单价值',
                    formula: 'GMV / 订单数',
                    formulaExpression: 'metric:order_amount / metric:order_count',
                    businessImportance: 'HIGH',
                    certificationLevel: 'OFFICIAL',
                    category: '交易指标',
                    embedding: [],
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 CompositeMetric: avg_order_value")

        logger.info("  共创建: 2个AtomicMetric, 1个DerivedMetric, 1个CompositeMetric")

    def _create_metric_relationships(self):
        """创建指标关系"""
        logger.info("\n步骤 4/7: 创建指标关系...")

        with self.driver.session() as session:
            # AtomicMetric -> Column (原子指标度量字段)
            session.run("""
                MATCH (m:AtomicMetric {name: 'order_amount'})
                MATCH (c:Column {name: 'order_amount'})
                CREATE (m)-[:MEASURES {generatedAt: datetime()}]->(c)
            """)
            logger.info("  ✓ AtomicMetric(order_amount) -[:MEASURES]-> Column(order_amount)")

            # DerivedMetric -> AtomicMetric (派生指标来源于原子指标)
            session.run("""
                MATCH (dm:DerivedMetric {name: 'gmv_last_7days'})
                MATCH (am:AtomicMetric {name: 'order_amount'})
                CREATE (dm)-[:DERIVED_FROM {
                    modifier: 'TIME',
                    generatedAt: datetime()
                }]->(am)
            """)
            logger.info("  ✓ DerivedMetric(gmv_last_7days) -[:DERIVED_FROM]-> AtomicMetric(order_amount)")

            # CompositeMetric -> AtomicMetric (复合指标计算来源)
            session.run("""
                MATCH (cm:CompositeMetric {name: 'avg_order_value'})
                MATCH (am1:AtomicMetric {name: 'order_amount'})
                MATCH (am2:AtomicMetric {name: 'order_count'})
                CREATE (cm)-[:COMPUTED_FROM {
                    role: 'NUMERATOR',
                    generatedAt: datetime()
                }]->(am1)
                CREATE (cm)-[:COMPUTED_FROM {
                    role: 'DENOMINATOR',
                    generatedAt: datetime()
                }]->(am2)
            """)
            logger.info("  ✓ CompositeMetric(avg_order_value) -[:COMPUTED_FROM]-> AtomicMetric(order_amount)")
            logger.info("  ✓ CompositeMetric(avg_order_value) -[:COMPUTED_FROM]-> AtomicMetric(order_count)")

        logger.info("  共创建: 4个指标关系")

    def _create_quality_rule_nodes(self):
        """创建质量规则节点"""
        logger.info("\n步骤 5/7: 创建质量规则节点...")

        with self.driver.session() as session:
            quality_rules = [
                {
                    "name": "rule_order_id_not_null",
                    "displayName": "订单ID不能为空",
                    "ruleType": "NOT_NULL",
                    "sqlExp": "order_id IS NOT NULL",
                    "isRequired": True,
                    "severity": "CRITICAL",
                    "isEnabled": True,
                    "description": "订单ID是核心字段，必须保证非空",
                },
                {
                    "name": "rule_order_amount_range",
                    "displayName": "订单金额范围检查",
                    "ruleType": "RANGE",
                    "sqlExp": "order_amount > 0 AND order_amount < 1000000",
                    "isRequired": True,
                    "severity": "HIGH",
                    "isEnabled": True,
                    "description": "订单金额必须在合理范围内（0-100万）",
                },
            ]

            for rule in quality_rules:
                session.run("""
                    CREATE (q:QualityRule:Knowledge {
                        name: $name,
                        displayName: $displayName,
                        ruleType: $ruleType,
                        sqlExp: $sqlExp,
                        isRequired: $isRequired,
                        severity: $severity,
                        isEnabled: $isEnabled,
                        description: $description,
                        embedding: [],
                        createdBy: 'MANUAL',
                        generatedAt: datetime(),
                        confidence: 1.0
                    })
                """, **rule)
                logger.info(f"  ✓ 创建 QualityRule: {rule['name']}")

        logger.info("  共创建: 2个QualityRule")

    def _create_quality_rule_relationships(self):
        """创建质量规则关系"""
        logger.info("\n步骤 6/7: 创建质量规则关系...")

        with self.driver.session() as session:
            # Column -> QualityRule
            session.run("""
                MATCH (c:Column {name: 'order_id'})
                MATCH (q:QualityRule {name: 'rule_order_id_not_null'})
                CREATE (c)-[:HAS_QUALITY_RULE {
                    priority: 10,
                    generatedAt: datetime(),
                    createdBy: 'MANUAL'
                }]->(q)
            """)
            logger.info("  ✓ Column(order_id) -[:HAS_QUALITY_RULE]-> QualityRule(rule_order_id_not_null)")

            session.run("""
                MATCH (c:Column {name: 'order_amount'})
                MATCH (q:QualityRule {name: 'rule_order_amount_range'})
                CREATE (c)-[:HAS_QUALITY_RULE {
                    priority: 8,
                    generatedAt: datetime(),
                    createdBy: 'MANUAL'
                }]->(q)
            """)
            logger.info("  ✓ Column(order_amount) -[:HAS_QUALITY_RULE]-> QualityRule(rule_order_amount_range)")

        logger.info("  共创建: 2个质量规则关系")

    def _create_join_nodes_and_relationships(self):
        """创建 Join 节点和列级 JOIN 关系"""
        logger.info("\n步骤 6.5/7: 创建 Join 节点和关系...")

        with self.driver.session() as session:
            # Join 1: ods_order LEFT JOIN ods_user ON order.user_id = user.user_id
            session.run("""
                CREATE (j:Join:Knowledge {
                    id: 'join_order_user',
                    name: 'order_user_join',
                    displayName: '订单-用户关联',
                    joinType: 'LEFT_JOIN',
                    description: '订单左连接用户表，获取用户信息',
                    cardinality: 'N:1',
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 Join: order_user_join")

            # 创建 JOIN_LEFT 关系：ods_order.user_id -> Join
            session.run("""
                MATCH (j:Join {id: 'join_order_user'})
                MATCH (order_user_id:Column {name: 'user_id'})
                     <-[:HAS_COLUMN]-(order_table:Table {name: 'ods_order'})
                CREATE (order_user_id)-[:JOIN_LEFT {
                    createdAt: datetime()
                }]->(j)
            """)
            logger.info("  ✓ Column(ods_order.user_id) -[:JOIN_LEFT]-> Join(join_order_user)")

            # 创建 JOIN_RIGHT 关系：Join -> ods_user.user_id
            session.run("""
                MATCH (j:Join {id: 'join_order_user'})
                MATCH (user_id:Column {name: 'user_id'})
                     <-[:HAS_COLUMN]-(user_table:Table {name: 'ods_user'})
                CREATE (j)-[:JOIN_RIGHT {
                    createdAt: datetime()
                }]->(user_id)
            """)
            logger.info("  ✓ Join(join_order_user) -[:JOIN_RIGHT]-> Column(ods_user.user_id)")

            # Join 2: ods_order LEFT JOIN ods_product ON order.product_id = product.product_id
            session.run("""
                CREATE (j:Join:Knowledge {
                    id: 'join_order_product',
                    name: 'order_product_join',
                    displayName: '订单-商品关联',
                    joinType: 'LEFT_JOIN',
                    description: '订单左连接商品表，获取商品信息',
                    cardinality: 'N:1',
                    createdBy: 'MANUAL',
                    generatedAt: datetime(),
                    confidence: 1.0
                })
            """)
            logger.info("  ✓ 创建 Join: order_product_join")

            # 创建 JOIN_LEFT 关系：ods_order.product_id -> Join
            session.run("""
                MATCH (j:Join {id: 'join_order_product'})
                MATCH (order_product_id:Column {name: 'product_id'})
                     <-[:HAS_COLUMN]-(order_table:Table {name: 'ods_order'})
                CREATE (order_product_id)-[:JOIN_LEFT {
                    createdAt: datetime()
                }]->(j)
            """)
            logger.info("  ✓ Column(ods_order.product_id) -[:JOIN_LEFT]-> Join(join_order_product)")

            # 创建 JOIN_RIGHT 关系：Join -> ods_product.product_id
            session.run("""
                MATCH (j:Join {id: 'join_order_product'})
                MATCH (product_id:Column {name: 'product_id'})
                     <-[:HAS_COLUMN]-(product_table:Table {name: 'ods_product'})
                CREATE (j)-[:JOIN_RIGHT {
                    createdAt: datetime()
                }]->(product_id)
            """)
            logger.info("  ✓ Join(join_order_product) -[:JOIN_RIGHT]-> Column(ods_product.product_id)")

        logger.info("  共创建: 2个Join节点, 4条JOIN关系 (2个JOIN_LEFT + 2个JOIN_RIGHT)")


def main():
    """主函数"""
    logger.info("🚀 Neo4j 知识图谱示例数据初始化工具 v3.0")

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

        # 初始化示例数据
        initializer = KnowledgeGraphDataInitializer(driver)
        initializer.initialize()

        logger.info("\n下一步:")
        logger.info("  运行 python scripts/generate_embeddings.py 生成向量嵌入")

    except Exception as e:
        logger.error(f"初始化失败: {e}")
        raise
    finally:
        driver.close()
        logger.info("Neo4j连接已关闭")


if __name__ == "__main__":
    main()
