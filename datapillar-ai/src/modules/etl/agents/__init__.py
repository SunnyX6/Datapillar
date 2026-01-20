"""
ETL 智能团队

使用 @agent 装饰器定义 Agent，通过 Datapillar 组建团队。

团队成员：
- AnalystAgent: 需求分析师（入口，兼顾智能分发）
- CatalogAgent: 元数据问答专员
- ArchitectAgent: 数据架构师
- DeveloperAgent: 数据开发工程师
- ReviewerAgent: 代码评审员

协作模式：Process.DYNAMIC
- AnalystAgent 作为入口，可分发到 CatalogAgent 或 ArchitectAgent
- ArchitectAgent → DeveloperAgent → ReviewerAgent 形成开发流水线
"""

# 显式导入工具模块，触发 @tool 装饰器注册
from src.modules.etl import tools as _tools  # noqa: F401
from datapillar_oneagentic import Datapillar, DatapillarConfig, Process
from src.infrastructure.llm.config import get_datapillar_config

# 显式导入所有 agent 模块，触发 @agent 装饰器注册
from . import (
    analyst_agent,
    architect_agent,
    catalog_agent,
    developer_agent,
    reviewer_agent,
)

def create_etl_team(
    *,
    config: DatapillarConfig | None = None,
    namespace: str | None = None,
) -> Datapillar:
    """创建 ETL 智能团队

    Returns:
        Datapillar: 配置好的 ETL 团队实例

    协作流程（DYNAMIC 模式）：
    1. AnalystAgent 接收请求，判断意图
       - 元数据查询 → 委派给 CatalogAgent
       - ETL 需求 → 分析后委派给 ArchitectAgent
    2. ArchitectAgent 设计 Job/Stage → 委派给 DeveloperAgent
    3. DeveloperAgent 生成 SQL → 委派给 ReviewerAgent
    4. ReviewerAgent 评审代码 → 返回结果
    """
    from .analyst_agent import AnalystAgent
    from .architect_agent import ArchitectAgent
    from .catalog_agent import CatalogAgent
    from .developer_agent import DeveloperAgent
    from .reviewer_agent import ReviewerAgent

    if config is None:
        config = get_datapillar_config()
    if not namespace:
        namespace = "etl_team"

    return Datapillar(
        config=config,
        namespace=namespace,
        name="ETL 智能团队",
        agents=[
            AnalystAgent,  # 入口：需求分析 + 智能分发
            CatalogAgent,  # 元数据查询
            ArchitectAgent,  # 架构设计
            DeveloperAgent,  # SQL 开发
            ReviewerAgent,  # 代码评审
        ],
        process=Process.DYNAMIC,
    )


__all__ = [
    "analyst_agent",
    "architect_agent",
    "catalog_agent",
    "developer_agent",
    "reviewer_agent",
    "create_etl_team",
]
