"""
清空 Neo4j 数据库
删除所有节点、关系、约束和索引
"""

import sys
from pathlib import Path
import os
import logging

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 添加项目根目录到Python路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from neo4j import GraphDatabase

# 直接从环境变量读取配置，避免导入 src
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")


def clear_neo4j_database(driver):
    """清空 Neo4j 数据库"""

    logger.warning("=" * 80)
    logger.warning("⚠️  准备清空 Neo4j 数据库（删除所有节点、关系、约束和索引）")
    logger.warning("=" * 80)

    try:
        with driver.session() as session:
            # 1. 删除所有节点和关系
            logger.info("\n步骤 1/3: 删除所有节点和关系...")
            session.run("MATCH (n) DETACH DELETE n")
            logger.info("✅ 所有节点和关系已删除")

            # 2. 删除所有约束
            logger.info("\n步骤 2/3: 删除所有约束...")
            result = session.run("SHOW CONSTRAINTS")
            constraints = [record.data() for record in result]
            if constraints:
                for constraint in constraints:
                    constraint_name = constraint.get("name")
                    if constraint_name:
                        try:
                            session.run(f"DROP CONSTRAINT {constraint_name} IF EXISTS")
                            logger.info(f"  ✓ 已删除约束: {constraint_name}")
                        except Exception as e:
                            logger.warning(f"  ⚠ 删除约束 {constraint_name} 失败: {e}")
                logger.info(f"✅ 已删除 {len(constraints)} 个约束")
            else:
                logger.info("✅ 没有约束需要删除")

            # 3. 删除所有索引
            logger.info("\n步骤 3/3: 删除所有索引...")
            result = session.run("SHOW INDEXES")
            indexes = [record.data() for record in result]
            if indexes:
                for index in indexes:
                    index_name = index.get("name")
                    index_type = index.get("type", "")
                    # 跳过约束自动创建的索引（它们会随约束一起删除）
                    if index_name and "CONSTRAINT" not in index_type.upper():
                        try:
                            session.run(f"DROP INDEX {index_name} IF EXISTS")
                            logger.info(f"  ✓ 已删除索引: {index_name}")
                        except Exception as e:
                            logger.warning(f"  ⚠ 删除索引 {index_name} 失败: {e}")
                logger.info(f"✅ 已处理 {len(indexes)} 个索引")
            else:
                logger.info("✅ 没有索引需要删除")

        logger.info("\n" + "=" * 80)
        logger.info("✅ Neo4j 数据库已完全清空！")
        logger.info("=" * 80)

    except Exception as e:
        logger.error(f"清空数据库失败: {e}")
        raise


def main():
    """主函数"""
    logger.info("🧹 Neo4j 数据库清空工具")

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

        # 清空数据库
        clear_neo4j_database(driver)

    except Exception as e:
        logger.error(f"操作失败: {e}")
        raise
    finally:
        driver.close()
        logger.info("Neo4j连接已关闭")


if __name__ == "__main__":
    main()
