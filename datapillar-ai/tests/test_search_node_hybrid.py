"""
Neo4jNodeSearch 搜索测试

测试目标：
1. hybrid_search - 元数据指针检索（默认排除 SQL）
2. search_reference_sql - 参考 SQL 搜索

测试场景：
1. 指针检索：精准表名、语义表名、跨节点描述
2. SQL 搜索：自然语言描述、SQL 片段

运行方式：
    python tests/test_search_node_hybrid.py --all
"""

import logging
import os
import sys
from pathlib import Path

project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from neo4j import GraphDatabase

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")
NEO4J_DATABASE = os.getenv("NEO4J_DATABASE", "neo4j")


# ==================== 测试数据定义 ====================


TEST_NODES = [
    # Catalog
    {
        "id": "test_catalog_dw",
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
        "id": "test_table_order_info",
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
        "id": "test_table_user_info",
        "labels": ["Knowledge", "Table"],
        "name": "user_info",
        "description": "用户信息表，存储用户基本信息，包含用户ID、用户名、手机号、注册时间等",
    },
    {
        "id": "test_table_dwd_trade",
        "labels": ["Knowledge", "Table"],
        "name": "dwd_trade_order",
        "description": "交易订单宽表，清洗后的订单数据，用于统计分析，包含交易金额、交易状态等核心字段",
    },
    # Column - 金额相关
    {
        "id": "test_col_order_amount",
        "labels": ["Knowledge", "Column"],
        "name": "order_amount",
        "description": "订单金额，订单总金额，单位：元，精确到分",
    },
    {
        "id": "test_col_trade_amount",
        "labels": ["Knowledge", "Column"],
        "name": "trade_amount",
        "description": "交易金额，实际交易金额，扣除优惠后的最终支付金额",
    },
    {
        "id": "test_col_order_status",
        "labels": ["Knowledge", "Column"],
        "name": "order_status",
        "description": "订单状态，订单当前状态，关联订单状态值域",
    },
    {
        "id": "test_col_user_id",
        "labels": ["Knowledge", "Column"],
        "name": "user_id",
        "description": "用户ID，关联用户信息表",
    },
    # ValueDomain - 订单状态
    {
        "id": "test_vd_order_status",
        "labels": ["Knowledge", "ValueDomain"],
        "name": "ORDER_STATUS",
        "description": "订单状态枚举值域，定义订单的生命周期状态：待支付、已支付、已发货、已完成、已取消",
    },
    {
        "id": "test_vd_trade_type",
        "labels": ["Knowledge", "ValueDomain"],
        "name": "TRADE_TYPE",
        "description": "交易类型枚举值域，定义不同的交易方式：普通交易、秒杀交易、预售交易",
    },
    # SQL 节点（用于测试参考 SQL 搜索）
    {
        "id": "test_sql_order_agg",
        "labels": ["Knowledge", "SQL"],
        "content": "SELECT user_id, SUM(trade_amount) as total_amount FROM dwd_trade_order GROUP BY user_id",
        "summary": "按用户汇总交易金额，从交易订单宽表计算每个用户的总交易额",
        "tags": "聚合,用户,交易金额",
        "dialect": "spark",
        "engine": "spark",
    },
    {
        "id": "test_sql_order_status",
        "labels": ["Knowledge", "SQL"],
        "content": "SELECT order_status, COUNT(*) as cnt FROM order_info GROUP BY order_status",
        "summary": "统计各订单状态的数量分布，用于订单状态分析",
        "tags": "聚合,订单状态,统计",
        "dialect": "spark",
        "engine": "spark",
    },
]

# 关系定义
TEST_RELATIONSHIPS = [
    ("test_catalog_dw", "HAS_SCHEMA", "test_schema_ods"),
    ("test_catalog_dw", "HAS_SCHEMA", "test_schema_dwd"),
    ("test_schema_ods", "HAS_TABLE", "test_table_order_info"),
    ("test_schema_ods", "HAS_TABLE", "test_table_order_detail"),
    ("test_schema_ods", "HAS_TABLE", "test_table_user_info"),
    ("test_schema_dwd", "HAS_TABLE", "test_table_dwd_trade"),
    ("test_table_order_info", "HAS_COLUMN", "test_col_order_amount"),
    ("test_table_order_info", "HAS_COLUMN", "test_col_order_status"),
    ("test_table_order_info", "HAS_COLUMN", "test_col_user_id"),
    ("test_table_dwd_trade", "HAS_COLUMN", "test_col_trade_amount"),
    ("test_col_order_status", "HAS_VALUE_DOMAIN", "test_vd_order_status"),
]


# ==================== 数据操作 ====================


def get_driver():
    """获取 Neo4j 驱动"""
    return GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))


def get_embedder():
    """获取项目的 UnifiedEmbedder"""
    from src.infrastructure.llm.embeddings import UnifiedEmbedder

    return UnifiedEmbedder()


def seed_test_data():
    """创建测试数据"""
    print("\n" + "=" * 80)
    print("生成测试数据")
    print("=" * 80)

    embedder = get_embedder()
    print(f"  使用 embedding 模型: {embedder.provider}/{embedder.model_name}")
    driver = get_driver()

    # 生成 embedding
    texts_for_embed = []
    for node in TEST_NODES:
        text_parts = [
            node.get("name", ""),
            node.get("description", ""),
            node.get("summary", ""),
            node.get("tags", ""),
        ]
        text = " ".join(filter(None, text_parts))
        texts_for_embed.append(text)

    print(f"生成 {len(texts_for_embed)} 个节点的 embedding...")
    embeddings = embedder.embed_batch(texts_for_embed)

    # 写入节点
    with driver.session(database=NEO4J_DATABASE) as session:
        for i, node in enumerate(TEST_NODES):
            labels_str = ":".join(node["labels"])
            props = {k: v for k, v in node.items() if k != "labels"}
            props["embedding"] = embeddings[i]

            cypher = f"""
            MERGE (n:{labels_str} {{id: $id}})
            SET n += $props
            RETURN n.id AS id
            """
            result = session.run(cypher, {"id": node["id"], "props": props})
            record = result.single()
            if record:
                print(f"  创建节点: {record['id']}")

    # 创建关系
    with driver.session(database=NEO4J_DATABASE) as session:
        for from_id, rel_type, to_id in TEST_RELATIONSHIPS:
            cypher = f"""
            MATCH (a {{id: $from_id}}), (b {{id: $to_id}})
            MERGE (a)-[r:{rel_type}]->(b)
            RETURN type(r) AS rel
            """
            session.run(cypher, {"from_id": from_id, "to_id": to_id})
        print(f"  创建 {len(TEST_RELATIONSHIPS)} 条关系")

    driver.close()
    print("测试数据生成完成")


def cleanup_test_data():
    """清理测试数据"""
    print("\n清理测试数据...")
    driver = get_driver()
    with driver.session(database=NEO4J_DATABASE) as session:
        result = session.run(
            "MATCH (n) WHERE n.id STARTS WITH 'test_' DETACH DELETE n RETURN count(n) AS cnt"
        )
        record = result.single()
        print(f"  删除 {record['cnt']} 个测试节点")
    driver.close()


# ==================== 测试函数 ====================


def test_hybrid_search():
    """测试 hybrid_search 元数据指针检索（默认排除 SQL）"""
    from src.infrastructure.repository.kg.search_node import Neo4jNodeSearch

    test_cases = [
        # 场景1: 精准表名
        {"query": "order_info", "desc": "精准表名（英文）", "expected_types": ["Table"]},
        {"query": "user_info", "desc": "精准表名（用户表）", "expected_types": ["Table"]},
        # 场景2: 语义表名
        {"query": "订单表", "desc": "语义表名（中文）", "expected_types": ["Table"]},
        {"query": "用户信息", "desc": "语义表名（中文）", "expected_types": ["Table"]},
        {"query": "交易数据", "desc": "语义表名（中文）", "expected_types": ["Table"]},
        # 场景3: 跨节点描述
        {
            "query": "订单状态",
            "desc": "跨节点（列+值域）",
            "expected_types": ["Column", "ValueDomain"],
        },
        {"query": "交易金额", "desc": "跨节点（列）", "expected_types": ["Column"]},
    ]

    print("\n" + "=" * 80)
    print("Neo4jNodeSearch.hybrid_search 元数据指针检索测试（默认排除 SQL）")
    print("=" * 80)

    results = []
    for case in test_cases:
        query = case["query"]
        desc = case["desc"]
        expected_types = case["expected_types"]

        print(f"\n--- {desc}: '{query}' ---")
        print(f"    期望类型: {expected_types}")

        try:
            hits = Neo4jNodeSearch.hybrid_search(
                query=query,
                top_k=10,
                min_score=0.5,
            )

            if not hits:
                print("    [无结果] ❌")
                results.append({"case": desc, "success": False, "reason": "无结果"})
                continue

            print(f"    返回 {len(hits)} 个节点:")
            found_types = set()
            has_sql = False
            for hit in hits:
                labels = [lbl for lbl in hit.labels if lbl != "Knowledge"]
                labels_str = ", ".join(labels) if labels else "无标签"
                found_types.update(labels)
                if "SQL" in labels:
                    has_sql = True
                print(f"      - [{labels_str}] {hit.name or hit.node_id} (score={hit.score:.3f})")
                if hit.description:
                    desc_preview = (
                        hit.description[:40] + "..."
                        if len(hit.description) > 40
                        else hit.description
                    )
                    print(f"        描述: {desc_preview}")

            # 验证：不应该包含 SQL 节点
            if has_sql:
                print("    ✗ 不应返回 SQL 节点！")
                results.append({"case": desc, "success": False, "reason": "返回了 SQL 节点"})
                continue

            # 验证是否命中期望类型
            hit_expected = any(t in found_types for t in expected_types)
            if hit_expected:
                print("    ✓ 命中期望类型，无 SQL 节点")
                results.append({"case": desc, "success": True})
            else:
                print(f"    ✗ 未命中期望类型，实际: {found_types}")
                results.append(
                    {"case": desc, "success": False, "reason": f"类型不匹配: {found_types}"}
                )

        except Exception as e:
            print(f"    [错误] {e}")
            results.append({"case": desc, "success": False, "reason": str(e)})
            logger.exception("hybrid_search 失败")

    # 汇总结果
    print("\n" + "=" * 80)
    print("测试结果汇总")
    print("=" * 80)
    success_count = sum(1 for r in results if r["success"])
    print(f"通过: {success_count}/{len(results)}")
    for r in results:
        status = "✓" if r["success"] else "✗"
        reason = "" if r["success"] else f" - {r.get('reason', '')}"
        print(f"  {status} {r['case']}{reason}")

    return results


def test_search_reference_sql():
    """测试 search_reference_sql 参考 SQL 搜索"""
    from src.infrastructure.repository.kg.search_sql import Neo4jSQLSearch

    test_cases = [
        # 自然语言描述
        {"query": "用户交易金额汇总", "desc": "自然语言描述"},
        {"query": "订单状态统计", "desc": "自然语言描述"},
        # SQL 片段
        {
            "query": "SELECT user_id, SUM(amount) FROM orders GROUP BY user_id",
            "desc": "SQL 片段（聚合）",
        },
        {"query": "SELECT status, COUNT(*) FROM order", "desc": "SQL 片段（统计）"},
    ]

    print("\n" + "=" * 80)
    print("Neo4jNodeSearch.search_reference_sql 参考 SQL 搜索测试")
    print("=" * 80)

    results = []
    for case in test_cases:
        query = case["query"]
        desc = case["desc"]

        print(f"\n--- {desc}: '{query[:50]}{'...' if len(query) > 50 else ''}' ---")

        try:
            hits = Neo4jSQLSearch.search_reference_sql(
                query=query,
                top_k=5,
                min_score=0.5,
            )

            if not hits:
                print("    [无结果] ❌")
                results.append({"case": desc, "success": False, "reason": "无结果"})
                continue

            print(f"    返回 {len(hits)} 条参考 SQL:")
            for hit in hits:
                print(f"      - [{hit.dialect}/{hit.engine}] score={hit.score:.3f}")
                print(f"        摘要: {hit.summary}")
                if hit.content:
                    content_preview = (
                        hit.content[:60] + "..." if len(hit.content) > 60 else hit.content
                    )
                    print(f"        SQL: {content_preview}")

            print(f"    ✓ 找到 {len(hits)} 条参考 SQL")
            results.append({"case": desc, "success": True})

        except Exception as e:
            print(f"    [错误] {e}")
            results.append({"case": desc, "success": False, "reason": str(e)})
            logger.exception("search_reference_sql 失败")

    # 汇总结果
    print("\n" + "=" * 80)
    print("测试结果汇总")
    print("=" * 80)
    success_count = sum(1 for r in results if r["success"])
    print(f"通过: {success_count}/{len(results)}")
    for r in results:
        status = "✓" if r["success"] else "✗"
        reason = "" if r["success"] else f" - {r.get('reason', '')}"
        print(f"  {status} {r['case']}{reason}")

    return results


def test_node_type_filter():
    """测试节点类型过滤"""
    from src.infrastructure.repository.kg.search_node import Neo4jNodeSearch

    query = "订单"
    node_types_list = [
        None,  # 不过滤
        ["Table"],
        ["Column"],
        ["ValueDomain"],
        ["SQL"],
    ]

    print("\n" + "=" * 80)
    print(f"节点类型过滤测试 - query: '{query}'")
    print("=" * 80)

    for node_types in node_types_list:
        filter_desc = str(node_types) if node_types else "无过滤"
        print(f"\n--- 过滤条件: {filter_desc} ---")

        try:
            hits = Neo4jNodeSearch.hybrid_search(
                query=query,
                top_k=10,
                min_score=0.5,
                node_types=node_types,
            )

            if not hits:
                print("    [无结果]")
                continue

            print(f"    返回 {len(hits)} 个节点:")
            for hit in hits:
                labels = [lbl for lbl in hit.labels if lbl != "Knowledge"]
                labels_str = ", ".join(labels) if labels else "无标签"
                print(f"      - [{labels_str}] {hit.name or hit.node_id} (score={hit.score:.3f})")

        except Exception as e:
            print(f"    [错误] {e}")


def test_vector_vs_fulltext():
    """对比向量搜索和全文搜索"""
    from src.infrastructure.repository.kg.search_node import Neo4jNodeSearch

    queries = [
        "order_info",  # 精准匹配 - 全文应该更好
        "订单交易金额",  # 语义匹配 - 向量应该更好
    ]

    print("\n" + "=" * 80)
    print("向量搜索 vs 全文搜索 对比")
    print("=" * 80)

    for query in queries:
        print(f"\n--- Query: '{query}' ---")

        # 向量搜索
        print("\n  [向量搜索]")
        vector_hits = Neo4jNodeSearch.vector_search(query=query, top_k=5, min_score=0.5)
        if vector_hits:
            for hit in vector_hits:
                labels = [lbl for lbl in hit.labels if lbl != "Knowledge"]
                print(f"    - [{', '.join(labels)}] {hit.name} (score={hit.score:.3f})")
        else:
            print("    无结果")

        # 全文搜索
        print("\n  [全文搜索]")
        fulltext_hits = Neo4jNodeSearch.fulltext_search(query=query, top_k=5)
        if fulltext_hits:
            for hit in fulltext_hits:
                labels = [lbl for lbl in hit.labels if lbl != "Knowledge"]
                print(f"    - [{', '.join(labels)}] {hit.name} (score={hit.score:.3f})")
        else:
            print("    无结果")

        # 混合搜索
        print("\n  [混合搜索]")
        hybrid_hits = Neo4jNodeSearch.hybrid_search(query=query, top_k=5, min_score=0.5)
        if hybrid_hits:
            for hit in hybrid_hits:
                labels = [lbl for lbl in hit.labels if lbl != "Knowledge"]
                print(f"    - [{', '.join(labels)}] {hit.name} (score={hit.score:.3f})")
        else:
            print("    无结果")


# ==================== 主入口 ====================


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Neo4jNodeSearch 混合搜索测试")
    parser.add_argument("--seed", action="store_true", help="生成测试数据")
    parser.add_argument("--cleanup", action="store_true", help="清理测试数据")
    parser.add_argument("--test", action="store_true", help="运行测试")
    parser.add_argument("--all", action="store_true", help="生成数据 + 运行测试")

    args = parser.parse_args()

    if args.cleanup:
        cleanup_test_data()
    elif args.seed:
        seed_test_data()
    elif args.test:
        test_hybrid_search()
        test_search_reference_sql()
        test_node_type_filter()
        test_vector_vs_fulltext()
    elif args.all:
        seed_test_data()
        test_hybrid_search()
        test_search_reference_sql()
        test_node_type_filter()
        test_vector_vs_fulltext()
    else:
        print("用法:")
        print("  --seed     生成测试数据")
        print("  --cleanup  清理测试数据")
        print("  --test     运行测试（需要先生成数据）")
        print("  --all      生成数据 + 运行测试")
