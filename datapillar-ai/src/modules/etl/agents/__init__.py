# @author Sunny
# @date 2026-01-27

"""
ETL smart team

use @agent Decorator definition Agent,Pass Datapillar Build a team.team member:- AnalystAgent:Demand Analyst(entrance,Taking into account intelligent distribution)
- CatalogAgent:Metadata Q&A Specialist
- ArchitectAgent:data architect
- DeveloperAgent:Data development engineer
- ReviewerAgent:code reviewer

Collaboration mode:Process.DYNAMIC
- AnalystAgent as an entrance,Can be distributed to CatalogAgent or ArchitectAgent
- ArchitectAgent → DeveloperAgent → ReviewerAgent Form a development pipeline
"""

# Explicitly import tool modules,trigger @tool Decorator registration
from datapillar_oneagentic import Datapillar, DatapillarConfig, Process

from src.infrastructure.llm.config import get_datapillar_config
from src.modules.etl import tools as _tools  # noqa: F401
from src.shared.config.runtime import get_default_tenant_id

# Explicitly import all agent module,trigger @agent Decorator registration
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
    tenant_id: int | None = None,
) -> Datapillar:
    """create ETL smart team

    Returns:Datapillar:configured ETL Team instance

    Collaboration process(DYNAMIC mode):1.AnalystAgent receive request,Determine intent
    - Metadata query → delegate to CatalogAgent
    - ETL demand → After analysis,delegate to ArchitectAgent
    2.ArchitectAgent design Job/Stage → delegate to DeveloperAgent
    3.DeveloperAgent generate SQL → delegate to ReviewerAgent
    4.ReviewerAgent Review code → Return results
    """
    from .analyst_agent import AnalystAgent
    from .architect_agent import ArchitectAgent
    from .catalog_agent import CatalogAgent
    from .developer_agent import DeveloperAgent
    from .reviewer_agent import ReviewerAgent

    resolved_tenant_id = tenant_id or get_default_tenant_id()
    if config is None:
        config = get_datapillar_config(resolved_tenant_id)
    if not namespace:
        namespace = f"etl_team_{resolved_tenant_id}"

    return Datapillar(
        config=config,
        namespace=namespace,
        name="ETL smart team",
        agents=[
            AnalystAgent,  # entrance:needs analysis + Intelligent distribution
            CatalogAgent,  # Metadata query
            ArchitectAgent,  # Architecture design
            DeveloperAgent,  # SQL develop
            ReviewerAgent,  # code review],process=Process.DYNAMIC,)
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
