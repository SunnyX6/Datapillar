"""
需求分析师 Agent 测试

测试场景：
1. 简单同步：用户表同步到维度表
2. 复杂清洗：多表关联 + 值域验证
3. 聚合统计：按天统计订单
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


# ==================== 测试数据 ====================


def get_test_data():
    """完整测试数据"""
    catalogs = [
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
            "description": "ERP 系统 MySQL 数据库",
        },
    ]

    schemas = [
        {
            "id": "test_schema_ods_20231201",
            "labels": ["Knowledge", "Schema"],
            "name": "ods_20231201",
            "description": "ODS 原始数据层",
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
            "id": "test_schema_erp_trade",
            "labels": ["Knowledge", "Schema"],
            "name": "erp_trade",
            "description": "ERP 交易模块",
            "catalog_id": "test_cat_mysql_erp",
        },
    ]

    tags = [
        {
            "id": "test_tag_order_domain",
            "labels": ["Knowledge", "Tag"],
            "name": "订单域",
            "description": "订单相关数据资产",
        },
        {
            "id": "test_tag_user_domain",
            "labels": ["Knowledge", "Tag"],
            "name": "用户域",
            "description": "用户相关数据资产",
        },
        {
            "id": "test_tag_ods_layer",
            "labels": ["Knowledge", "Tag"],
            "name": "ODS层",
            "description": "原始数据层",
        },
        {
            "id": "test_tag_dwd_layer",
            "labels": ["Knowledge", "Tag"],
            "name": "DWD层",
            "description": "明细数据层",
        },
    ]

    valuedomains = [
        {
            "id": "test_vd_order_status",
            "labels": ["Knowledge", "ValueDomain"],
            "name": "ORDER_STATUS",
            "domainCode": "ORDER_STATUS",
            "domainName": "订单状态",
            "domainType": "ENUM",
            "description": "订单状态枚举",
            "items": json.dumps(
                [
                    {"value": "CREATED", "label": "待支付"},
                    {"value": "PAID", "label": "已支付"},
                    {"value": "CANCELLED", "label": "已取消"},
                    {"value": "COMPLETED", "label": "已完成"},
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
            "description": "支付方式枚举",
            "items": json.dumps(
                [
                    {"value": "ALIPAY", "label": "支付宝"},
                    {"value": "WECHAT", "label": "微信支付"},
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
            "description": "用户等级枚举",
            "items": json.dumps(
                [
                    {"value": "NORMAL", "label": "普通用户"},
                    {"value": "VIP", "label": "VIP会员"},
                ],
                ensure_ascii=False,
            ),
        },
    ]

    tables = [
        {
            "id": "test_table_t_ord_main",
            "labels": ["Knowledge", "Table"],
            "name": "t_ord_main",
            "description": "订单主表",
            "schema_id": "test_schema_ods_20231201",
            "tag_ids": ["test_tag_order_domain", "test_tag_ods_layer"],
        },
        {
            "id": "test_table_t_ord_dtl",
            "labels": ["Knowledge", "Table"],
            "name": "t_ord_dtl",
            "description": "订单明细表",
            "schema_id": "test_schema_ods_20231201",
            "tag_ids": ["test_tag_order_domain", "test_tag_ods_layer"],
        },
        {
            "id": "test_table_t_user_info",
            "labels": ["Knowledge", "Table"],
            "name": "t_user_info",
            "description": "用户信息表",
            "schema_id": "test_schema_erp_trade",
            "tag_ids": ["test_tag_user_domain", "test_tag_ods_layer"],
        },
        {
            "id": "test_table_order_detail_clean",
            "labels": ["Knowledge", "Table"],
            "name": "order_detail_clean",
            "description": "清洗后订单明细宽表",
            "schema_id": "test_schema_dw_core",
            "tag_ids": ["test_tag_order_domain", "test_tag_dwd_layer"],
        },
        {
            "id": "test_table_user_dim",
            "labels": ["Knowledge", "Table"],
            "name": "user_dim",
            "description": "用户维度表",
            "schema_id": "test_schema_dw_core",
            "tag_ids": ["test_tag_user_domain", "test_tag_dwd_layer"],
        },
    ]

    columns = [
        # t_ord_main
        {
            "id": "test_col_ord_sts",
            "labels": ["Knowledge", "Column"],
            "name": "ord_sts",
            "description": "订单状态",
            "dataType": "VARCHAR(20)",
            "table_id": "test_table_t_ord_main",
            "valuedomain_id": "test_vd_order_status",
        },
        {
            "id": "test_col_pay_type",
            "labels": ["Knowledge", "Column"],
            "name": "pay_type",
            "description": "支付方式",
            "dataType": "VARCHAR(20)",
            "table_id": "test_table_t_ord_main",
            "valuedomain_id": "test_vd_pay_type",
        },
        # t_user_info
        {
            "id": "test_col_usr_level",
            "labels": ["Knowledge", "Column"],
            "name": "usr_level",
            "description": "用户等级",
            "dataType": "VARCHAR(20)",
            "table_id": "test_table_t_user_info",
            "valuedomain_id": "test_vd_user_level",
        },
        # order_detail_clean
        {
            "id": "test_col_clean_order_status",
            "labels": ["Knowledge", "Column"],
            "name": "order_status",
            "description": "订单状态",
            "dataType": "VARCHAR(20)",
            "table_id": "test_table_order_detail_clean",
            "valuedomain_id": "test_vd_order_status",
        },
    ]

    return {
        "catalogs": catalogs,
        "schemas": schemas,
        "tags": tags,
        "valuedomains": valuedomains,
        "tables": tables,
        "columns": columns,
    }


# ==================== Fixtures ====================


@pytest.fixture(scope="module")
def neo4j_driver():
    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
    driver.verify_connectivity()
    yield driver
    driver.close()


@pytest.fixture(scope="module", autouse=True)
def cleanup_neo4j_client():
    yield
    from src.infrastructure.database import Neo4jClient

    Neo4jClient.close()


@pytest.fixture(scope="module")
def seed_test_data(neo4j_driver):
    """创建测试数据"""
    from src.infrastructure.llm.embeddings import UnifiedEmbedder

    embedder = UnifiedEmbedder()

    data = get_test_data()
    all_nodes = (
        data["catalogs"]
        + data["schemas"]
        + data["tags"]
        + data["valuedomains"]
        + data["tables"]
        + data["columns"]
    )

    # 生成 embedding
    texts = [
        " ".join(
            filter(None, [n.get("name", ""), n.get("domainName", ""), n.get("description", "")])
        )
        for n in all_nodes
    ]
    embeddings = embedder.embed_batch(texts)

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
            session.run(
                f"MERGE (n:{labels_str} {{id: $id}}) SET n += $props",
                {"id": node["id"], "props": props},
            )

    # 创建关系
    relationships = []
    for schema in data["schemas"]:
        if "catalog_id" in schema:
            relationships.append((schema["catalog_id"], "HAS_SCHEMA", schema["id"]))
    for table in data["tables"]:
        if "schema_id" in table:
            relationships.append((table["schema_id"], "HAS_TABLE", table["id"]))
        for tag_id in table.get("tag_ids", []):
            relationships.append((table["id"], "HAS_TAG", tag_id))
    for column in data["columns"]:
        if "table_id" in column:
            relationships.append((column["table_id"], "HAS_COLUMN", column["id"]))
        if "valuedomain_id" in column:
            relationships.append((column["id"], "HAS_VALUE_DOMAIN", column["valuedomain_id"]))

    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        for from_id, rel_type, to_id in relationships:
            session.run(
                f"MATCH (a {{id: $from_id}}), (b {{id: $to_id}}) MERGE (a)-[:{rel_type}]->(b)",
                {"from_id": from_id, "to_id": to_id},
            )

    yield data

    # 清理
    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        session.run("MATCH (n) WHERE n.id STARTS WITH 'test_' DETACH DELETE n")


@pytest.fixture(scope="module")
def knowledge_agent():
    from src.modules.etl.agents.knowledge_agent import KnowledgeAgent

    return KnowledgeAgent()


@pytest.fixture(scope="module")
def analyst_agent():
    from src.modules.etl.agents.analyst_agent import AnalystAgent

    return AnalystAgent()


# ==================== 测试用例 ====================


class TestAnalystAgent:
    """需求分析师 Agent 测试"""

    @pytest.mark.asyncio
    async def test_simple_sync_analysis(self, seed_test_data, analyst_agent, knowledge_agent):
        """简单同步：用户表同步到维度表"""
        user_query = "把 t_user_info 用户表同步到 user_dim 用户维度表"

        print("\n" + "=" * 60)
        print("【测试】简单同步分析")
        print("=" * 60)
        print(f"用户需求: {user_query}")

        result = await analyst_agent.run(user_query=user_query, knowledge_agent=knowledge_agent)

        print(f"\n分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        assert result.status == "completed", f"分析应该完成，而不是 {result.status}"
        assert result.deliverable is not None

        analysis = result.deliverable
        print(f"\n业务步骤数: {len(analysis.steps)}")
        for step in analysis.steps:
            print(f"  - {step.step_name}: {step.description}")

    @pytest.mark.asyncio
    async def test_complex_cleaning_analysis(self, seed_test_data, analyst_agent, knowledge_agent):
        """复杂清洗：多表关联 + 值域验证"""
        user_query = """把订单主表 t_ord_main 和订单明细表 t_ord_dtl 关联，清洗后写入 order_detail_clean
要求：
1. 过滤掉已取消的订单
2. 只保留支付宝和微信支付的订单
3. 关联用户表获取用户等级和VIP标记"""

        print("\n" + "=" * 60)
        print("【测试】复杂清洗分析")
        print("=" * 60)
        print(f"用户需求: {user_query}")

        result = await analyst_agent.run(user_query=user_query, knowledge_agent=knowledge_agent)

        print(f"\n分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        if result.deliverable:
            analysis = result.deliverable
            print(f"\n业务步骤数: {len(analysis.steps)}")
            for step in analysis.steps:
                print(f"  - {step.step_name}: {step.description}")

        # 验证：LLM 应该使用值域中的正确值（CANCELLED 而不是 cancel）

    @pytest.mark.asyncio
    async def test_aggregation_analysis(self, seed_test_data, analyst_agent, knowledge_agent):
        """聚合统计：按天统计订单"""
        user_query = "基于清洗后的订单明细表，按天统计订单数量和金额，写入订单日汇总表"

        print("\n" + "=" * 60)
        print("【测试】聚合统计分析")
        print("=" * 60)
        print(f"用户需求: {user_query}")

        result = await analyst_agent.run(user_query=user_query, knowledge_agent=knowledge_agent)

        print(f"\n分析状态: {result.status}")
        print(f"分析摘要: {result.summary}")

        if result.deliverable:
            analysis = result.deliverable
            print(f"\n业务步骤数: {len(analysis.steps)}")
            for step in analysis.steps:
                print(f"  - {step.step_name}: {step.description}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
