# @author Sunny
# @date 2026-01-27

"""
Knowledge navigation tools

Provide data warehouse knowledge navigation（summary + Type tool list）
"""

import json
import logging

from pydantic import BaseModel

from src.infrastructure.repository.knowledge import Neo4jNodeSearch
from src.modules.etl.tools.registry import REGISTRY, etl_tool

logger = logging.getLogger(__name__)

_BASE_NAV_TYPES = [
    {"type": "Catalog", "children": ["Schema"]},
    {"type": "Schema", "children": ["Table", "Metric"]},
    {"type": "Table", "children": ["Column", "SQL", "Tag"]},
    {"type": "Column", "children": ["ValueDomain", "Tag"]},
    {"type": "Metric", "children": ["Metric", "Column"]},
    {"type": "SQL", "children": ["Table"]},
    {"type": "Tag", "children": []},
    {"type": "ValueDomain", "children": []},
]


class KnowledgeNavigationInput(BaseModel):
    """Get the parameters of data warehouse knowledge navigation（no parameters）"""


def _tool_error(message: str) -> str:
    """Constructor error response"""
    return json.dumps({"error": message}, ensure_ascii=False)


def _tool_success(data: dict) -> str:
    """Constructor responds successfully"""
    return json.dumps(data, ensure_ascii=False)


def _build_nav_types() -> list[dict[str, object]]:
    known = {item["type"] for item in _BASE_NAV_TYPES}
    extra_types = sorted(
        {meta.tool_type for meta in REGISTRY.list_all() if meta.tool_type not in known}
    )
    nav_types = list(_BASE_NAV_TYPES)
    for tool_type in extra_types:
        nav_types.append({"type": tool_type, "children": []})
    return nav_types


def build_knowledge_navigation_tool(allowed_tools: list[str]):
    allowed_set = {name for name in allowed_tools if isinstance(name, str)}
    allowed_set.add("get_knowledge_navigation")

    @etl_tool(
        "get_knowledge_navigation",
        tool_type="Navigation",
        desc="Data warehouse knowledge navigation",
        args_schema=KnowledgeNavigationInput,
    )
    def get_knowledge_navigation() -> str:
        """
        Get data warehouse knowledge navigation（summary + Type tool list）

        Return fields：
        - summary: Total number of assets of each type
        - navigation: Type hierarchy and list of available tools（Not bound to specific objects）
        """
        logger.info("get_knowledge_navigation()")

        summary = Neo4jNodeSearch.get_knowledge_navigation()
        if summary is None:
            return _tool_error("Failed to obtain data warehouse knowledge navigation")

        navigation = []
        for item in _build_nav_types():
            tools = [
                {"name": tool.name, "desc": tool.desc}
                for tool in REGISTRY.list_by_type(item["type"])
                if tool.name in allowed_set
            ]
            navigation.append(
                {
                    "type": item["type"],
                    "children": item["children"],
                    "tools": tools,
                }
            )

        return _tool_success({"summary": summary, "navigation": navigation})

    return get_knowledge_navigation


NODE_TOOLS: list = []
