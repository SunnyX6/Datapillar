"""
知识服务全局检索测试

测试场景：验证全局检索能返回多种节点类型（Table、Column、ValueDomain）
用户输入："订单交易金额统计"
期望返回：订单表、金额列、交易状态值域
"""

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
def knowledge_agent():
    """KnowledgeAgent 实例"""
    from src.modules.etl.agents.knowledge_agent import KnowledgeAgent

    return KnowledgeAgent()


@pytest.fixture(scope="module")
def seed_test_data(neo4j_driver, embedder):
    """创建测试数据（按实际数据模型，只使用 name + description）"""
    test_nodes = [
        # Catalog
        {
            "id": "test_catalog_1",
            "labels": ["Knowledge", "Catalog"],
            "name": "数仓",
            "description": "企业级数据仓库，包含ODS、DWD、DWS、ADS等分层",
        },
        # Schema
        {
            "id": "test_schema_ods",
            "labels": ["Knowledge", "Schema"],
            "name": "ods",
            "description": "原始数据层，存放业务系统抽取的原始数据，订单、用户、商品等",
        },
        {
            "id": "test_schema_dwd",
            "labels": ["Knowledge", "Schema"],
            "name": "dwd",
            "description": "明细数据层，存放清洗后的明细数据，交易明细、订单明细等",
        },
        # Table - 订单相关
        {
            "id": "test_table_order",
            "labels": ["Knowledge", "Table"],
            "name": "order_info",
            "description": "订单信息表，存储订单基本信息，包含订单ID、用户ID、订单金额、订单状态、创建时间等",
        },
        {
            "id": "test_table_order_detail",
            "labels": ["Knowledge", "Table"],
            "name": "order_detail",
            "description": "订单明细表，存储订单商品明细，包含订单ID、商品ID、商品数量、商品单价、商品金额等",
        },
        {
            "id": "test_table_order_clean",
            "labels": ["Knowledge", "Table"],
            "name": "order_clean",
            "description": "订单清洗表，清洗后的订单数据，用于统计分析，包含交易金额、交易状态等核心字段",
        },
        # Column - 金额相关
        {
            "id": "test_col_order_amount",
            "labels": ["Knowledge", "Column"],
            "name": "order_amount",
            "description": "订单金额，订单总金额，单位：元，精确到分",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
        },
        {
            "id": "test_col_trade_amount",
            "labels": ["Knowledge", "Column"],
            "name": "trade_amount",
            "description": "交易金额，实际交易金额，扣除优惠后的最终支付金额",
            "dataType": "DECIMAL(18,2)",
            "nullable": True,
        },
        {
            "id": "test_col_order_status",
            "labels": ["Knowledge", "Column"],
            "name": "order_status",
            "description": "订单状态，订单当前状态，关联订单状态值域",
            "dataType": "VARCHAR(20)",
            "nullable": True,
        },
        # ValueDomain - 订单状态
        {
            "id": "test_vd_order_status",
            "labels": ["Knowledge", "ValueDomain"],
            "description": "订单状态枚举值域，定义订单的生命周期状态",
            "domainCode": "ORDER_STATUS",
            "domainName": "订单状态",
            "domainType": "ENUM",
            "domainLevel": "BUSINESS",
            "dataType": "STRING",
            "items": '[{"value":"CREATED","label":"待支付"},{"value":"PAID","label":"已支付"},{"value":"COMPLETED","label":"已完成"}]',
        },
        {
            "id": "test_vd_trade_type",
            "labels": ["Knowledge", "ValueDomain"],
            "description": "交易类型枚举值域，定义不同的交易方式",
            "domainCode": "TRADE_TYPE",
            "domainName": "交易类型",
            "domainType": "ENUM",
            "domainLevel": "BUSINESS",
            "dataType": "STRING",
            "items": '[{"value":"NORMAL","label":"普通交易"},{"value":"FLASH","label":"秒杀交易"}]',
        },
    ]

    # 生成 embedding（使用 name + description）
    texts_for_embed = []
    for node in test_nodes:
        text = f"{node.get('name', '')} {node.get('domainName', '')} {node.get('description', '')}"
        texts_for_embed.append(text)

    embeddings = embedder.embed_batch(texts_for_embed)

    # 写入 Neo4j
    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        for i, node in enumerate(test_nodes):
            labels_str = ":".join(node["labels"])
            props = {k: v for k, v in node.items() if k != "labels"}
            props["embedding"] = embeddings[i]

            cypher = f"""
            MERGE (n:{labels_str} {{id: $id}})
            SET n += $props
            RETURN elementId(n) AS eid
            """
            session.run(cypher, {"id": node["id"], "props": props})

    # 创建关系（层级关系通过图关系表达）
    relationships = [
        ("test_catalog_1", "HAS_SCHEMA", "test_schema_ods"),
        ("test_catalog_1", "HAS_SCHEMA", "test_schema_dwd"),
        ("test_schema_ods", "HAS_TABLE", "test_table_order"),
        ("test_schema_ods", "HAS_TABLE", "test_table_order_detail"),
        ("test_schema_dwd", "HAS_TABLE", "test_table_order_clean"),
        ("test_table_order", "HAS_COLUMN", "test_col_order_amount"),
        ("test_table_order", "HAS_COLUMN", "test_col_order_status"),
        ("test_table_order_clean", "HAS_COLUMN", "test_col_trade_amount"),
        ("test_col_order_status", "HAS_VALUE_DOMAIN", "test_vd_order_status"),
    ]

    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        for from_id, rel_type, to_id in relationships:
            cypher = f"""
            MATCH (a {{id: $from_id}}), (b {{id: $to_id}})
            MERGE (a)-[r:{rel_type}]->(b)
            RETURN type(r) AS rel
            """
            session.run(cypher, {"from_id": from_id, "to_id": to_id})

    yield test_nodes

    # 清理测试数据
    with neo4j_driver.session(database=NEO4J_DATABASE) as session:
        session.run("MATCH (n) WHERE n.id STARTS WITH 'test_' DETACH DELETE n")


# ==================== Test Cases ====================


class TestKnowledgeGlobalSearch:
    """知识服务全局检索测试"""

    @pytest.mark.asyncio
    async def test_global_search_returns_multiple_types(self, seed_test_data, knowledge_agent):
        """测试全局检索能返回多种节点类型"""
        query = "订单交易金额统计"

        pointers = await knowledge_agent.global_search(query, top_k=20, min_score=0.5)

        assert len(pointers) > 0, "应该返回至少一个指针"

        # 收集返回的节点类型
        returned_types = set()
        for p in pointers:
            if p.primary_label:
                returned_types.add(p.primary_label)

        print(f"\n查询: {query}")
        print(f"返回 {len(pointers)} 个指针")
        print(f"节点类型: {returned_types}")
        for p in pointers:
            print(f"  - [{p.primary_label}] {p.name} (score={p.score:.3f})")

        # 验证返回了多种类型
        assert len(returned_types) >= 2, f"应该返回至少 2 种节点类型，实际: {returned_types}"

    @pytest.mark.asyncio
    async def test_global_search_returns_tools(self, seed_test_data, knowledge_agent):
        """测试全局检索返回的指针包含工具信息"""
        query = "订单表"

        pointers = await knowledge_agent.global_search(query, top_k=10, min_score=0.5)

        assert len(pointers) > 0, "应该返回至少一个指针"

        # 收集可用工具
        available_tools = knowledge_agent.get_available_tools(pointers)

        print(f"\n查询: {query}")
        print(f"可用工具: {available_tools}")

        # Table 类型的指针应该包含表级工具
        table_pointers = knowledge_agent.filter_pointers_by_type(pointers, "Table")
        if table_pointers:
            assert "get_table_columns" in available_tools, "Table 指针应该支持 get_table_columns"
            assert "get_table_lineage" in available_tools, "Table 指针应该支持 get_table_lineage"

    @pytest.mark.asyncio
    async def test_run_returns_pointers_and_tools(self, seed_test_data, knowledge_agent):
        """测试 run() 方法返回指针和可用工具"""
        result = await knowledge_agent.run(user_query="订单金额")

        assert result.status == "completed", f"状态应该是 completed，实际: {result.status}"
        assert result.deliverable is not None, "deliverable 不应为空"

        deliverable = result.deliverable
        assert "pointers" in deliverable, "deliverable 应该包含 pointers"
        assert "available_tools" in deliverable, "deliverable 应该包含 available_tools"

        print("\n查询: 订单金额")
        print(f"返回 {len(deliverable['pointers'])} 个指针")
        print(f"可用工具: {deliverable['available_tools']}")

    @pytest.mark.asyncio
    async def test_filter_pointers_by_type(self, seed_test_data, knowledge_agent):
        """测试按类型过滤指针"""
        query = "订单交易"

        pointers = await knowledge_agent.global_search(query, top_k=20, min_score=0.5)

        table_pointers = knowledge_agent.filter_pointers_by_type(pointers, "Table")
        column_pointers = knowledge_agent.filter_pointers_by_type(pointers, "Column")
        valuedomain_pointers = knowledge_agent.filter_pointers_by_type(pointers, "ValueDomain")

        print(f"\n查询: {query}")
        print(f"Table 指针: {len(table_pointers)}")
        print(f"Column 指针: {len(column_pointers)}")
        print(f"ValueDomain 指针: {len(valuedomain_pointers)}")

        # 验证过滤后的指针类型正确
        for p in table_pointers:
            assert p.primary_label == "Table", f"过滤后的指针应该是 Table 类型: {p.primary_label}"

    @pytest.mark.asyncio
    async def test_filter_pointers_by_tool(self, seed_test_data, knowledge_agent):
        """测试按工具过滤指针"""
        query = "订单"

        pointers = await knowledge_agent.global_search(query, top_k=20, min_score=0.5)

        # 过滤支持 get_table_columns 的指针
        column_tool_pointers = knowledge_agent.filter_pointers_by_tool(
            pointers, "get_table_columns"
        )

        print(f"\n查询: {query}")
        print(f"支持 get_table_columns 的指针: {len(column_tool_pointers)}")
        for p in column_tool_pointers:
            print(f"  - {p.name} ({p.primary_label})")

        # 验证过滤后的指针都支持该工具
        for p in column_tool_pointers:
            assert "get_table_columns" in p.tools, f"指针应该支持 get_table_columns: {p.tools}"


# ==================== 直接运行 ====================


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
