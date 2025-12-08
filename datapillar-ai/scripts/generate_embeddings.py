"""
Neo4j 知识图谱向量嵌入生成脚本
为所有节点生成 embedding 向量，用于向量检索
"""

import os
from typing import List, Dict, Any

import logging
logger = logging.getLogger(__name__)
from neo4j import GraphDatabase
from sqlalchemy import create_engine, text
from zai import ZhipuAiClient

# 直接从环境变量读取配置
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "data_ai_builder")
MYSQL_USERNAME = os.getenv("MYSQL_USERNAME", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "Sunny.123456")


def get_embedding_model_config() -> Dict[str, Any]:
    """从 MySQL 获取默认 Embedding 模型配置"""
    try:
        db_url = (
            f"mysql+pymysql://{MYSQL_USERNAME}:{MYSQL_PASSWORD}"
            f"@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}"
            f"?charset=utf8mb4"
        )
        engine = create_engine(db_url)

        with engine.connect() as conn:
            query = text("""
                SELECT provider, model_name, api_key, base_url, embedding_dimension
                FROM ai_model
                WHERE is_enabled = 1 AND is_default = 1 AND model_type = 'embedding'
                LIMIT 1
            """)
            result = conn.execute(query)
            row = result.fetchone()

            if row:
                return {
                    "provider": row[0],
                    "model_name": row[1],
                    "api_key": row[2],
                    "base_url": row[3],
                    "embedding_dimension": int(row[4]) if row[4] else 2048
                }

        raise ValueError("未找到默认 Embedding 模型配置")

    except Exception as e:
        logger.error(f"获取 Embedding 模型配置失败: {e}")
        raise


def create_embedding(text: str, model_config: Dict[str, Any]) -> List[float]:
    """
    生成文本的向量嵌入

    Args:
        text: 要转换的文本
        model_config: 模型配置

    Returns:
        向量嵌入列表
    """
    provider = model_config["provider"].lower()

    if provider == "glm":
        client = ZhipuAiClient(
            api_key=model_config["api_key"],
            base_url=model_config["base_url"]
        )
        response = client.embeddings.create(
            model=model_config["model_name"],
            input=text
        )
        # 提取embedding向量
        if hasattr(response, "data") and len(response.data) > 0:
            return response.data[0].embedding
        elif isinstance(response, dict) and "data" in response:
            return response["data"][0]["embedding"]
        else:
            raise ValueError(f"无法从Embedding响应中提取向量: {response}")

    elif provider in ["openai", "deepseek"]:
        from openai import OpenAI
        client = OpenAI(
            api_key=model_config["api_key"],
            base_url=model_config["base_url"]
        )
        response = client.embeddings.create(
            model=model_config["model_name"],
            input=text
        )
        return response.data[0].embedding

    else:
        raise ValueError(f"不支持的Embedding模型提供商: {provider}")


class EmbeddingGenerator:
    """向量嵌入生成器"""

    def __init__(self, driver, model_config: Dict[str, Any]):
        self.driver = driver
        self.model_config = model_config
        self.total_nodes = 0
        self.processed_nodes = 0

    def generate_all_embeddings(self):
        """为所有节点生成 embedding"""
        logger.info("=" * 80)
        logger.info("开始生成知识图谱节点向量嵌入")
        logger.info(f"使用模型: {self.model_config['provider']}/{self.model_config['model_name']}")
        logger.info(f"向量维度: {self.model_config['embedding_dimension']}")
        logger.info("=" * 80)

        # 定义所有节点类型和它们的文本字段
        node_configs = [
            # 第一梯队：业务域导航
            ("Domain", ["name", "displayName", "description", "businessGoals"]),
            ("Catalog", ["name", "displayName", "description", "dataScope", "tags"]),
            ("Subject", ["name", "displayName", "description", "tags"]),
            ("Schema", ["name", "displayName", "description"]),

            # 第二梯队：资产定位
            ("Table", ["name", "displayName", "description", "businessValue", "tags", "certificationLevel"]),

            # 第三梯队：细节查询
            ("Column", ["name", "displayName", "description", "dataType"]),

            # 指标体系
            ("AtomicMetric", ["name", "displayName", "description", "category", "metricType", "unit"]),
            ("DerivedMetric", ["name", "displayName", "description", "category", "timeModifier", "formula"]),
            ("CompositeMetric", ["name", "displayName", "description", "category", "formula", "formulaExpression", "businessImportance", "certificationLevel"]),

            # 质量规则
            ("QualityRule", ["name", "displayName", "description", "ruleType", "severity", "sqlExp"]),
        ]

        for node_label, text_fields in node_configs:
            self._generate_embeddings_for_label(node_label, text_fields)

        logger.info("\n" + "=" * 80)
        logger.info(f"✅ 向量嵌入生成完成！共处理 {self.processed_nodes}/{self.total_nodes} 个节点")
        logger.info("=" * 80)

    def _generate_embeddings_for_label(self, label: str, text_fields: List[str]):
        """为指定类型的节点生成 embedding"""
        logger.info(f"\n处理节点类型: {label}")

        with self.driver.session() as session:
            # 1. 获取所有该类型的节点
            query = f"MATCH (n:{label}) RETURN n"
            result = session.run(query)
            nodes = [record["n"] for record in result]

            if not nodes:
                logger.info(f"  ⚠ 未找到 {label} 类型的节点，跳过")
                return

            self.total_nodes += len(nodes)
            logger.info(f"  找到 {len(nodes)} 个 {label} 节点")

            # 2. 为每个节点生成 embedding
            for i, node in enumerate(nodes, 1):
                try:
                    # 提取节点的文本内容
                    text_parts = []
                    for field in text_fields:
                        value = node.get(field)
                        if value:
                            if isinstance(value, list):
                                text_parts.append(", ".join(str(v) for v in value))
                            else:
                                text_parts.append(str(value))

                    if not text_parts:
                        logger.warning(f"  ⚠ 节点 {node.element_id} 没有可用的文本字段，跳过")
                        continue

                    # 合并文本
                    combined_text = " | ".join(text_parts)

                    # 生成 embedding
                    embedding = create_embedding(combined_text, self.model_config)

                    # 更新节点的 embedding 字段
                    update_query = f"""
                    MATCH (n:{label})
                    WHERE elementId(n) = $element_id
                    SET n.embedding = $embedding
                    """
                    session.run(update_query, {
                        "element_id": node.element_id,
                        "embedding": embedding
                    })

                    self.processed_nodes += 1

                    if i % 10 == 0 or i == len(nodes):
                        logger.info(f"  进度: {i}/{len(nodes)} ({i*100//len(nodes)}%)")

                except Exception as e:
                    logger.error(f"  ✗ 节点 {node.element_id} embedding 生成失败: {e}")
                    continue

            logger.info(f"  ✓ {label} 类型节点处理完成")


def main():
    """主函数"""
    logger.info("🚀 Neo4j 知识图谱向量嵌入生成工具")

    # 1. 获取 Embedding 模型配置
    logger.info("获取 Embedding 模型配置...")
    model_config = get_embedding_model_config()
    logger.info(f"模型配置: {model_config['provider']}/{model_config['model_name']}")

    # 2. 连接 Neo4j
    logger.info(f"连接Neo4j数据库: {NEO4J_URI}")
    driver = GraphDatabase.driver(
        NEO4J_URI,
        auth=(NEO4J_USERNAME, NEO4J_PASSWORD),
    )

    try:
        # 测试连接
        driver.verify_connectivity()
        logger.info("Neo4j连接成功")

        # 3. 生成所有 embeddings
        generator = EmbeddingGenerator(driver, model_config)
        generator.generate_all_embeddings()

    except Exception as e:
        logger.error(f"生成失败: {e}")
        raise
    finally:
        driver.close()
        logger.info("Neo4j连接已关闭")


if __name__ == "__main__":
    main()
