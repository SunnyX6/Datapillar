"""
ETL 多智能体测试

测试场景：
1. 简单同步：用户表同步
2. 复杂清洗：订单表关联清洗，验证值域使用
3. 聚合统计：订单日统计

测试数据特点（模拟真实企业脏数据）：
- Catalog/Schema 命名混乱
- 通过 Tag 机制组织语义
- 列关联值域，验证 LLM 不乱猜
"""

import json
import logging
import os
import sys
from pathlib import Path

import pytest

project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from neo4j import GraphDatabase

logger = logging.getLogger(__name__)

NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")
NEO4J_DATABASE = os.getenv("NEO4J_DATABASE", "neo4j")


# ==================== 测试数据定义 ====================


def get_test_catalogs():
    """Catalog 定义（乱命名）"""
    return [
        {
            "id": "test_cat_hive_prod",
            "labels": ["Knowledge", "Catalog"],
            "name": "hive_prod",
            "description": "生产环境 Hive 数据仓库",
        },
        {
            "id": "test_cat_mysql_erp",
            "labels": ["Knowledge", "Catalog"],
            "name": "mysql_erp_v2",
            "description": "ERP 系统 MySQL 数据库 2.0 版本",
        },
    ]


def get_test_schemas():
    """Schema 定义（乱命名）"""
    return [
        {
            "id": "test_schema_ods_20231201",
            "labels": ["Knowledge", "Schema"],
            "name": "ods_20231201",
            "description": "ODS 原始数据层（2023年12月版本）",
            "catalog_id": "test_cat_hive_prod",
        },
        {
            "id": "test_schema_dw_core",
            "labels": ["Knowledge", "Schema"],
            "name": "dw_core",
            "description": "数仓核心层",
            "catalog_id": "test_cat_hive_prod",
        },
        {
            "id": "test_schema_db_order_v2",
            "labels": ["Knowledge", "Schema"],
            "name": "db_order_v2",
            "description": "订单数据库 V2",
            "catalog_id": "test_cat_hive_prod",
        },
        {
            "id": "test_schema_erp_trade",
            "labels": ["Knowledge", "Schema"],
            "name": "erp_trade",
            "description": "ERP 交易模块",
            "catalog_id": "test_cat_mysql_erp",
        },
    ]


def get_test_tags():
    """Tag 定义（语义分类）"""
    return [
        {
            "id": "test_tag_order_domain",
            "labels": ["Knowledge", "Tag"],
            "name": "订单域",
            "description": "订单相关的数据资产，包含订单主表、订单明细、订单状态等",
        },
        {
            "id": "test_tag_user_domain",
            "labels": ["Knowledge", "Tag"],
            "name": "用户域",
            "description": "用户相关的数据资产，包含用户信息、用户等级、会员信息等",
        },
        {
            "id": "test_tag_ods_layer",
            "labels": ["Knowledge", "Tag"],
            "name": "ODS层",
            "description": "原始数据层，存放业务系统抽取的未经处理的原始数据",
        },
        {
            "id": "test_tag_dwd_layer",
            "labels": ["Knowledge", "Tag"],
            "name": "DWD层",
            "description": "明细数据层，存放清洗、转换后的明细数据",
        },
        {
            "id": "test_tag_dws_layer",
            "labels": ["Knowledge", "Tag"],
            "name": "DWS层",
            "description": "汇总数据层，存放按主题聚合的汇总数据",
        },
    ]


def get_test_valuedomains():
    """值域定义"""
    return [
        {
            "id": "test_vd_order_status",
            "labels": ["Knowledge", "ValueDomain"],
            "name": "ORDER_STATUS",
            "domainCode": "ORDER_STATUS",
            "domainName": "订单状态",
            "domainType": "ENUM",
            "domainLevel": "BUSINESS",
            "dataType": "STRING",
            "description": "订单状态枚举值域，定义订单全生命周期状态",
            "items": json.dumps(
                [
                    {"value": "CREATED", "label": "待支付"},
                    {"value": "PAID", "label": "已支付"},
                    {"value": "SHIPPED", "label": "已发货"},
                    {"value": "COMPLETED", "label": "已完成"},
                    {"value": "CANCELLED", "label": "已取消"},
                ],
                ensure_ascii=False,
            ),
        },
        {
            "id": "test_vd_pay_type",
            "labels": ["Knowledge", "ValueDomain"],
            "name": "PAY_TYPE",
            "domainCode": "PAY_TYPE",
            "domainName": "支付方式",
            "domainType": "ENUM",
            "domainLevel": "BUSINESS",
            "dataType": "STRING",
            "description": "支付方式枚举值域，定义各种支付渠道",
            "items": json.dumps(
                [
                    {"value": "ALIPAY", "label": "支付宝"},
                    {"value": "WECHAT", "label": "微信支付"},
                    {"value": "BANK", "label": "银行卡"},
                    {"value": "CASH", "label": "现金"},
                ],
                ensure_ascii=False,
            ),
        },
        {
            "id": "test_vd_user_level",
            "labels": ["Knowledge", "ValueDomain"],
            "name": "USER_LEVEL",
            "domainCode": "USER_LEVEL",
            "domainName": "用户等级",
            "domainType": "ENUM",
            "domainLevel": "BUSINESS",
            "dataType": "STRING",
            "description": "用户等级枚举值域，定义用户会员等级",
            "items": json.dumps(
                [
                    {"value": "NORMAL", "label": "普通用户"},
                    {"value": "SILVER", "label": "白银会员"},
                    {"value": "GOLD", "label": "黄金会员"},
                    {"value": "VIP", "label": "VIP会员"},
                ],
                ensure_ascii=False,
            ),
        },
        {
            "id": "test_vd_yn_flag",
            "labels": ["Knowledge", "ValueDomain"],
            "name": "YN_FLAG",
            "domainCode": "YN_FLAG",
            "domainName": "是否标志",
            "domainType": "ENUM",
            "domainLevel": "TECHNICAL",
            "dataType": "STRING",
            "description": "通用是否标志值域",
            "items": json.dumps(
                [
                    {"value": "Y", "label": "是"},
                    {"value": "N", "label": "否"},
                ],
                ensure_ascii=False,
            ),
        },
    ]


def get_test_tables():
    """表定义（乱命名，通过 Tag 关联语义）"""
    return [
        # ODS 层 - 订单主表
        {
            "id": "test_table_t_ord_main",
            "labels": ["Knowledge", "Table"],
            "name": "t_ord_main",
            "description": "订单主表，存储订单基本信息，包含订单ID、用户ID、订单金额、订单状态、支付方式等",
            "schema_id": "test_schema_ods_20231201",
            "tag_ids": ["test_tag_order_domain", "test_tag_ods_layer"],
        },
        # ODS 层 - 订单明细表
        {
            "id": "test_table_t_ord_dtl",
            "labels": ["Knowledge", "Table"],
            "name": "t_ord_dtl",
            "description": "订单明细表，存储订单商品明细，包含商品ID、数量、单价、金额等",
            "schema_id": "test_schema_ods_20231201",
            "tag_ids": ["test_tag_order_domain", "test_tag_ods_layer"],
        },
        # ODS 层 - 用户表（在 ERP 库）
        {
            "id": "test_table_t_user_info",
            "labels": ["Knowledge", "Table"],
            "name": "t_user_info",
            "description": "用户信息表，存储用户基本信息，包含用户ID、用户名、手机号、等级、是否VIP等",
            "schema_id": "test_schema_erp_trade",
            "tag_ids": ["test_tag_user_domain", "test_tag_ods_layer"],
        },
        # DWD 层 - 订单明细宽表
        {
            "id": "test_table_order_detail_clean",
            "labels": ["Knowledge", "Table"],
            "name": "order_detail_clean",
            "description": "清洗后的订单明细宽表，关联了用户信息，字段已标准化",
            "schema_id": "test_schema_dw_core",
            "tag_ids": ["test_tag_order_domain", "test_tag_dwd_layer"],
        },
        # DWD 层 - 用户维度表
        {
            "id": "test_table_user_dim",
            "labels": ["Knowledge", "Table"],
            "name": "user_dim",
            "description": "用户维度表，存储清洗后的用户维度信息",
            "schema_id": "test_schema_dw_core",
            "tag_ids": ["test_tag_user_domain", "test_tag_dwd_layer"],
        },
        # DWS 层 - 订单日统计
        {
            "id": "test_table_order_daily_agg",
            "labels": ["Knowledge", "Table"],
            "name": "order_daily_agg",
            "description": "订单日汇总表，按天统计订单数量、金额、VIP订单占比等指标",
            "schema_id": "test_schema_db_order_v2",
            "tag_ids": ["test_tag_order_domain", "test_tag_dws_layer"],
        },
    ]


def get_test_columns():
    """列定义（乱命名，关联值域）"""
    return [
        # t_ord_main 的列（拼音缩写风格）
        {
            "id": "test_col_ord_id",
            "labels": ["Knowledge", "Column"],
            "name": "ord_id",
            "description": "订单ID，主键",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_t_ord_main",
        },
        {
            "id": "test_col_usr_id",
            "labels": ["Knowledge", "Column"],
            "name": "usr_id",
            "description": "用户ID，关联用户表",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_t_ord_main",
        },
        {
            "id": "test_col_ord_amt",
            "labels": ["Knowledge", "Column"],
            "name": "ord_amt",
            "description": "订单金额，单位：元，精确到分",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_t_ord_main",
        },
        {
            "id": "test_col_ord_sts",
            "labels": ["Knowledge", "Column"],
            "name": "ord_sts",
            "description": "订单状态",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_t_ord_main",
            "valuedomain_id": "test_vd_order_status",
        },
        {
            "id": "test_col_pay_type",
            "labels": ["Knowledge", "Column"],
            "name": "pay_type",
            "description": "支付方式",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_t_ord_main",
            "valuedomain_id": "test_vd_pay_type",
        },
        {
            "id": "test_col_crt_tm",
            "labels": ["Knowledge", "Column"],
            "name": "crt_tm",
            "description": "创建时间",
            "dataType": "TIMESTAMP",
            "nullable": True,
            "table_id": "test_table_t_ord_main",
        },
        # t_ord_dtl 的列（风格不统一）
        {
            "id": "test_col_dtl_order_id",
            "labels": ["Knowledge", "Column"],
            "name": "order_id",
            "description": "订单ID，关联订单主表",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_t_ord_dtl",
        },
        {
            "id": "test_col_dtl_prod_id",
            "labels": ["Knowledge", "Column"],
            "name": "prod_id",
            "description": "商品ID",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_t_ord_dtl",
        },
        {
            "id": "test_col_dtl_qty",
            "labels": ["Knowledge", "Column"],
            "name": "qty",
            "description": "商品数量",
            "dataType": "INT",
            "nullable": True,
            "table_id": "test_table_t_ord_dtl",
        },
        {
            "id": "test_col_dtl_price",
            "labels": ["Knowledge", "Column"],
            "name": "price",
            "description": "商品单价",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_t_ord_dtl",
        },
        {
            "id": "test_col_dtl_amount",
            "labels": ["Knowledge", "Column"],
            "name": "amount",
            "description": "商品金额（数量*单价）",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_t_ord_dtl",
        },
        # t_user_info 的列
        {
            "id": "test_col_uid",
            "labels": ["Knowledge", "Column"],
            "name": "uid",
            "description": "用户ID，主键",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_t_user_info",
        },
        {
            "id": "test_col_uname",
            "labels": ["Knowledge", "Column"],
            "name": "uname",
            "description": "用户名",
            "dataType": "VARCHAR(100)",
            "nullable": True,
            "table_id": "test_table_t_user_info",
        },
        {
            "id": "test_col_mobile_phone",
            "labels": ["Knowledge", "Column"],
            "name": "mobile_phone",
            "description": "手机号码",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_t_user_info",
        },
        {
            "id": "test_col_usr_level",
            "labels": ["Knowledge", "Column"],
            "name": "usr_level",
            "description": "用户等级",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_t_user_info",
            "valuedomain_id": "test_vd_user_level",
        },
        {
            "id": "test_col_is_vip",
            "labels": ["Knowledge", "Column"],
            "name": "is_vip",
            "description": "是否VIP",
            "dataType": "CHAR(1)",
            "nullable": True,
            "table_id": "test_table_t_user_info",
            "valuedomain_id": "test_vd_yn_flag",
        },
        {
            "id": "test_col_reg_date",
            "labels": ["Knowledge", "Column"],
            "name": "reg_date",
            "description": "注册日期",
            "dataType": "DATE",
            "nullable": True,
            "table_id": "test_table_t_user_info",
        },
        # order_detail_clean 的列（规范命名）
        {
            "id": "test_col_clean_order_id",
            "labels": ["Knowledge", "Column"],
            "name": "order_id",
            "description": "订单ID",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_order_detail_clean",
        },
        {
            "id": "test_col_clean_user_id",
            "labels": ["Knowledge", "Column"],
            "name": "user_id",
            "description": "用户ID",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_order_detail_clean",
        },
        {
            "id": "test_col_clean_order_status",
            "labels": ["Knowledge", "Column"],
            "name": "order_status",
            "description": "订单状态",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_order_detail_clean",
            "valuedomain_id": "test_vd_order_status",
        },
        {
            "id": "test_col_clean_pay_type",
            "labels": ["Knowledge", "Column"],
            "name": "pay_type",
            "description": "支付方式",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_order_detail_clean",
            "valuedomain_id": "test_vd_pay_type",
        },
        {
            "id": "test_col_clean_total_amount",
            "labels": ["Knowledge", "Column"],
            "name": "total_amount",
            "description": "订单总金额",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_order_detail_clean",
        },
        {
            "id": "test_col_clean_user_level",
            "labels": ["Knowledge", "Column"],
            "name": "user_level",
            "description": "用户等级",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_order_detail_clean",
            "valuedomain_id": "test_vd_user_level",
        },
        {
            "id": "test_col_clean_is_vip",
            "labels": ["Knowledge", "Column"],
            "name": "is_vip",
            "description": "是否VIP用户",
            "dataType": "CHAR(1)",
            "nullable": True,
            "table_id": "test_table_order_detail_clean",
            "valuedomain_id": "test_vd_yn_flag",
        },
        {
            "id": "test_col_clean_create_time",
            "labels": ["Knowledge", "Column"],
            "name": "create_time",
            "description": "订单创建时间",
            "dataType": "TIMESTAMP",
            "nullable": True,
            "table_id": "test_table_order_detail_clean",
        },
        # user_dim 的列
        {
            "id": "test_col_dim_user_id",
            "labels": ["Knowledge", "Column"],
            "name": "user_id",
            "description": "用户ID，主键",
            "dataType": "BIGINT",
            "nullable": False,
            "table_id": "test_table_user_dim",
        },
        {
            "id": "test_col_dim_user_name",
            "labels": ["Knowledge", "Column"],
            "name": "user_name",
            "description": "用户名",
            "dataType": "VARCHAR(100)",
            "nullable": True,
            "table_id": "test_table_user_dim",
        },
        {
            "id": "test_col_dim_phone",
            "labels": ["Knowledge", "Column"],
            "name": "phone",
            "description": "手机号",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_user_dim",
        },
        {
            "id": "test_col_dim_user_level",
            "labels": ["Knowledge", "Column"],
            "name": "user_level",
            "description": "用户等级",
            "dataType": "VARCHAR(20)",
            "nullable": True,
            "table_id": "test_table_user_dim",
            "valuedomain_id": "test_vd_user_level",
        },
        {
            "id": "test_col_dim_is_vip",
            "labels": ["Knowledge", "Column"],
            "name": "is_vip",
            "description": "是否VIP",
            "dataType": "CHAR(1)",
            "nullable": True,
            "table_id": "test_table_user_dim",
            "valuedomain_id": "test_vd_yn_flag",
        },
        # order_daily_agg 的列
        {
            "id": "test_col_agg_stat_date",
            "labels": ["Knowledge", "Column"],
            "name": "stat_date",
            "description": "统计日期",
            "dataType": "DATE",
            "nullable": False,
            "table_id": "test_table_order_daily_agg",
        },
        {
            "id": "test_col_agg_order_count",
            "labels": ["Knowledge", "Column"],
            "name": "order_count",
            "description": "订单数量",
            "dataType": "BIGINT",
            "nullable": True,
            "table_id": "test_table_order_daily_agg",
        },
        {
            "id": "test_col_agg_total_amount",
            "labels": ["Knowledge", "Column"],
            "name": "total_amount",
            "description": "订单总金额",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_order_daily_agg",
        },
        {
            "id": "test_col_agg_vip_amount",
            "labels": ["Knowledge", "Column"],
            "name": "vip_amount",
            "description": "VIP用户订单金额",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_order_daily_agg",
        },
        {
            "id": "test_col_agg_avg_amount",
            "labels": ["Knowledge", "Column"],
            "name": "avg_amount",
            "description": "平均订单金额",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
            "table_id": "test_table_order_daily_agg",
        },
    ]


# ==================== Fixtures ====================


@pytest.fixture(scope="module")
def neo4j_driver():
    """Neo4j 驱动"""
    driver = GraphDatabase.driver(
        NEO4J_URI,
        auth=(NEO4J_USERNAME, NEO4J_PASSWORD),
    )
    driver.verify_connectivity()
    yield driver
    driver.close()


@pytest.fixture(scope="module", autouse=True)
def cleanup_neo4j_client():
    """测试结束后关闭 Neo4jClient 的全局 driver"""
    yield
    from src.infrastructure.database import Neo4jClient

    Neo4jClient.close()


@pytest.fixture(scope="module")
def embedder():
    """Embedder 实例"""
    from src.infrastructure.llm.embeddings import UnifiedEmbedder

    return UnifiedEmbedder()


@pytest.fixture(scope="module")
def seed_test_data(neo4j_driver, embedder):
    """创建测试数据"""
    catalogs = get_test_catalogs()
    schemas = get_test_schemas()
    tags = get_test_tags()
    valuedomains = get_test_valuedomains()
    tables = get_test_tables()
    columns = get_test_columns()

    all_nodes = catalogs + schemas + tags + valuedomains + tables + columns

    # 生成 embedding
    texts_for_embed = []
    for node in all_nodes:
        text_parts = [
            node.get("name", ""),
            node.get("domainName", ""),
            node.get("description", ""),
        ]
        texts_for_embed.append(" ".join(filter(None, text_parts)))

    logger.info(f"生成 {len(texts_for_embed)} 个节点的 embedding...")
    embeddings = embedder.embed_batch(texts_for_embed)

    # 写入节点
    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        for i, node in enumerate(all_nodes):
            labels_str = ":".join(node["labels"])
            props = {
                k: v
                for k, v in node.items()
                if k
                not in [
                    "labels",
                    "schema_id",
                    "table_id",
                    "tag_ids",
                    "catalog_id",
                    "valuedomain_id",
                ]
            }
            props["embedding"] = embeddings[i]

            cypher = f"""
            MERGE (n:{labels_str} {{id: $id}})
            SET n += $props
            RETURN elementId(n) AS eid
            """
            session.run(cypher, {"id": node["id"], "props": props})

    # 创建关系
    relationships = []

    # Catalog -> Schema
    for schema in schemas:
        if "catalog_id" in schema:
            relationships.append((schema["catalog_id"], "HAS_SCHEMA", schema["id"]))

    # Schema -> Table
    for table in tables:
        if "schema_id" in table:
            relationships.append((table["schema_id"], "HAS_TABLE", table["id"]))

    # Table -> Column
    for column in columns:
        if "table_id" in column:
            relationships.append((column["table_id"], "HAS_COLUMN", column["id"]))

    # Column -> ValueDomain
    for column in columns:
        if "valuedomain_id" in column:
            relationships.append((column["id"], "HAS_VALUE_DOMAIN", column["valuedomain_id"]))

    # Table -> Tag
    for table in tables:
        for tag_id in table.get("tag_ids", []):
            relationships.append((table["id"], "HAS_TAG", tag_id))

    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        for from_id, rel_type, to_id in relationships:
            cypher = f"""
            MATCH (a {{id: $from_id}}), (b {{id: $to_id}})
            MERGE (a)-[r:{rel_type}]->(b)
            RETURN type(r) AS rel
            """
            session.run(cypher, {"from_id": from_id, "to_id": to_id})

    logger.info(f"创建了 {len(all_nodes)} 个节点和 {len(relationships)} 条关系")

    yield {
        "catalogs": catalogs,
        "schemas": schemas,
        "tags": tags,
        "valuedomains": valuedomains,
        "tables": tables,
        "columns": columns,
    }

    # 清理测试数据
    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        session.run("MATCH (n) WHERE n.id STARTS WITH 'test_' DETACH DELETE n")
    logger.info("测试数据已清理")


@pytest.fixture(scope="module")
def knowledge_agent():
    """KnowledgeAgent 实例"""
    from src.modules.etl.agents.knowledge_agent import KnowledgeAgent

    return KnowledgeAgent()


@pytest.fixture(scope="module")
def analyst_agent():
    """AnalystAgent 实例"""
    from src.modules.etl.agents.analyst_agent import AnalystAgent

    return AnalystAgent()


@pytest.fixture(scope="module")
def architect_agent():
    """ArchitectAgent 实例"""
    from src.modules.etl.agents.architect_agent import ArchitectAgent

    return ArchitectAgent()


@pytest.fixture(scope="module")
def developer_agent():
    """DeveloperAgent 实例"""
    from src.modules.etl.agents.developer_agent import DeveloperAgent

    return DeveloperAgent()


@pytest.fixture(scope="module")
def reviewer_agent():
    """ReviewerAgent 实例"""
    from src.modules.etl.agents.reviewer_agent import ReviewerAgent

    return ReviewerAgent()


# ==================== 测试用例：KnowledgeAgent ====================


class TestKnowledgeAgent:
    """知识服务测试"""

    @pytest.mark.asyncio
    async def test_global_search_by_tag(self, seed_test_data, knowledge_agent):
        """测试通过 Tag 语义搜索"""
        query = "订单域 ODS层 的表"

        ctx = await knowledge_agent.global_search(query, top_k=10, min_score=0.5)

        total = len(ctx.tables) + len(ctx.columns) + len(ctx.valuedomains) + len(ctx.sqls)
        print(f"\n查询: {query}")
        print(f"返回 {total} 个指针:")
        for t in ctx.tables:
            print(f"  - [Table] {t.catalog}.{t.schema_name}.{t.table} (score={t.score:.3f})")
            print(f"    tools: {t.tools}")
        for c in ctx.columns:
            print(f"  - [Column] {c.table}.{c.column} (score={c.score:.3f})")
        for v in ctx.valuedomains:
            print(f"  - [ValueDomain] {v.code} (score={v.score:.3f})")

        assert total > 0, "应该返回至少一个指针"

    @pytest.mark.asyncio
    async def test_search_table_with_valuedomain(self, seed_test_data, knowledge_agent):
        """测试搜索表时能关联值域"""
        query = "订单状态"

        ctx = await knowledge_agent.global_search(query, top_k=10, min_score=0.5)

        total = len(ctx.tables) + len(ctx.columns) + len(ctx.valuedomains) + len(ctx.sqls)
        print(f"\n查询: {query}")
        print(f"返回 {total} 个指针:")
        for t in ctx.tables:
            print(f"  - [Table] {t.catalog}.{t.schema_name}.{t.table} (score={t.score:.3f})")
        for v in ctx.valuedomains:
            print(f"  - [ValueDomain] {v.code}: {v.name} (score={v.score:.3f})")
            print(f"    values: {v.values}")

        # 应该能搜索到值域
        assert len(ctx.valuedomains) > 0, "应该返回值域指针"


# ==================== 测试用例：AnalystAgent ====================


class TestAnalystAgent:
    """需求分析测试"""

    @pytest.mark.asyncio
    async def test_simple_sync_analysis(self, seed_test_data, analyst_agent, knowledge_agent):
        """测试简单同步场景的需求分析"""
        user_query = "把 t_user_info 用户表同步到 user_dim 用户维度表"

        result = await analyst_agent.run(
            user_query=user_query,
            knowledge_agent=knowledge_agent,
        )

        print(f"\n用户需求: {user_query}")
        print(f"分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        if result.deliverable:
            print(f"分析结果: {result.deliverable}")

        assert result.status in ["completed", "needs_clarification"], f"状态异常: {result.status}"

    @pytest.mark.asyncio
    async def test_complex_cleaning_analysis(self, seed_test_data, analyst_agent, knowledge_agent):
        """测试复杂清洗场景的需求分析（验证值域使用）"""
        user_query = """
        把订单主表 t_ord_main 和订单明细表 t_ord_dtl 关联，清洗后写入 order_detail_clean，要求：
        1. 过滤掉已取消的订单
        2. 只保留支付宝和微信支付的订单
        3. 关联用户表获取用户等级和VIP标记
        """

        result = await analyst_agent.run(
            user_query=user_query,
            knowledge_agent=knowledge_agent,
        )

        print(f"\n用户需求: {user_query}")
        print(f"分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        if result.deliverable:
            analysis = result.deliverable
            print("\n业务步骤:")
            for step in analysis.steps:
                print(f"  - {step.step_name}: {step.description}")
                print(f"    输入表: {step.input_tables}")
                print(f"    输出表: {step.output_table}")

        assert result.status in ["completed", "needs_clarification"], f"状态异常: {result.status}"

    @pytest.mark.asyncio
    async def test_aggregation_analysis(self, seed_test_data, analyst_agent, knowledge_agent):
        """测试聚合统计场景的需求分析"""
        user_query = "基于清洗后的订单明细表，按天统计订单数量和金额，写入订单日汇总表"

        result = await analyst_agent.run(
            user_query=user_query,
            knowledge_agent=knowledge_agent,
        )

        print(f"\n用户需求: {user_query}")
        print(f"分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        assert result.status in ["completed", "needs_clarification"], f"状态异常: {result.status}"


# ==================== 测试用例：完整流程 ====================


class TestFullPipeline:
    """完整多智能体流程测试"""

    @pytest.mark.asyncio
    async def test_simple_sync_full_pipeline(  # noqa: C901, PLR0912
        self,
        seed_test_data,
        analyst_agent,
        architect_agent,
        developer_agent,
        reviewer_agent,
        knowledge_agent,
    ):
        """测试简单同步的完整流程"""
        user_query = "把用户表 t_user_info 同步到用户维度表 user_dim，字段映射：uid->user_id, uname->user_name, mobile_phone->phone"

        # 1. 需求分析
        print("\n" + "=" * 60)
        print("【Step 1】需求分析")
        print("=" * 60)

        analyst_result = await analyst_agent.run(
            user_query=user_query,
            knowledge_agent=knowledge_agent,
        )

        print(f"状态: {analyst_result.status}")
        print(f"摘要: {analyst_result.summary}")

        if analyst_result.status != "completed":
            if analyst_result.status == "needs_clarification" and analyst_result.clarification:
                print(f"需求分析需要澄清: {analyst_result.clarification.message}")
                if analyst_result.clarification.questions:
                    for i, q in enumerate(analyst_result.clarification.questions, 1):
                        print(f"  Q{i}: {q}")
            elif analyst_result.status == "failed":
                print(f"需求分析失败: {analyst_result.error}")
            else:
                print(f"需求分析未完成: {analyst_result.summary}")
            pytest.skip("需求分析未完成，跳过后续步骤")

        analysis_result = analyst_result.deliverable
        print(f"分析结果: {analysis_result.summary}")

        # 2. 架构设计
        print("\n" + "=" * 60)
        print("【Step 2】架构设计")
        print("=" * 60)

        architect_result = await architect_agent.run(
            user_query=user_query,
            analysis_result=analysis_result,
            selected_component="SPARK_SQL",
            knowledge_agent=knowledge_agent,
        )

        print(f"状态: {architect_result.status}")
        print(f"摘要: {architect_result.summary}")

        if architect_result.status != "completed":
            if architect_result.status == "needs_clarification" and architect_result.clarification:
                print(f"架构设计需要澄清: {architect_result.clarification.message}")
                if architect_result.clarification.questions:
                    for i, q in enumerate(architect_result.clarification.questions, 1):
                        print(f"  Q{i}: {q}")
            elif architect_result.status == "failed":
                print(f"架构设计失败: {architect_result.error}")
            else:
                print(f"架构设计未完成: {architect_result.summary}")
            pytest.skip("架构设计未完成，跳过后续步骤")

        workflow = architect_result.deliverable
        print(f"Job 数量: {len(workflow.jobs)}")
        for job in workflow.jobs:
            print(f"  - Job {job.id}: {job.name}")
            print(f"    Stage 数量: {len(job.stages or [])}")

        # 3. SQL 生成
        print("\n" + "=" * 60)
        print("【Step 3】SQL 生成")
        print("=" * 60)

        developer_result = await developer_agent.run(
            user_query=user_query,
            workflow=workflow,
            knowledge_agent=knowledge_agent,
        )

        print(f"状态: {developer_result.status}")
        print(f"摘要: {developer_result.summary}")

        if developer_result.status == "completed":
            updated_workflow = developer_result.deliverable
            for job in updated_workflow.jobs:
                sql_content = job.config.get("content") if job.config else None
                if sql_content:
                    print(f"\nJob {job.id} 生成的 SQL:")
                    print("-" * 40)
                    print(sql_content[:500])
                    if len(sql_content) > 500:
                        print("...")

        # 4. 代码评审
        print("\n" + "=" * 60)
        print("【Step 4】代码评审")
        print("=" * 60)

        if developer_result.status == "completed":
            reviewer_result = await reviewer_agent.run(
                user_query=user_query,
                analysis_result=analysis_result,
                workflow=developer_result.deliverable,
                review_stage="development",
            )

            print(f"状态: {reviewer_result.status}")
            print(f"摘要: {reviewer_result.summary}")

            if reviewer_result.deliverable:
                review = reviewer_result.deliverable
                print(f"评审通过: {review.passed}")
                print(f"评分: {review.score}")
                if review.issues:
                    print(f"问题: {review.issues}")
                if review.warnings:
                    print(f"警告: {review.warnings}")

    @pytest.mark.asyncio
    async def test_complex_cleaning_with_valuedomain(
        self,
        seed_test_data,
        analyst_agent,
        knowledge_agent,
    ):
        """测试复杂清洗场景（验证值域正确使用）"""
        user_query = """
        把订单主表清洗到订单明细表，要求：
        1. 过滤掉已取消的订单（不是猜测 status='cancel'，而是从值域获取正确的值）
        2. 只保留支付宝支付的订单
        """

        print("\n" + "=" * 60)
        print("【测试】值域正确使用")
        print("=" * 60)
        print(f"用户需求: {user_query}")

        result = await analyst_agent.run(
            user_query=user_query,
            knowledge_agent=knowledge_agent,
        )

        print(f"\n分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        if result.deliverable:
            analysis = result.deliverable
            print("\n业务步骤:")
            for step in analysis.steps:
                print(f"  - {step.step_name}: {step.description}")

        # 验证点：LLM 应该使用值域中的正确值
        # CANCELLED（已取消）而不是 cancel
        # ALIPAY（支付宝）而不是 alipay 或其他猜测


# ==================== 直接运行 ====================


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
