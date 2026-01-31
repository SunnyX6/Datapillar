# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识导航工具

提供数仓知识导航（summary + 类型工具列表）
"""

import json
import logging

from pydantic import BaseModel

from src.infrastructure.repository.kg import Neo4jNodeSearch
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
    """获取数仓知识导航的参数（无参数）"""


def _tool_error(message: str) -> str:
    """构造工具错误响应"""
    return json.dumps({"error": message}, ensure_ascii=False)


def _tool_success(data: dict) -> str:
    """构造工具成功响应"""
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
        desc="数仓知识导航",
        args_schema=KnowledgeNavigationInput,
    )
    def get_knowledge_navigation() -> str:
        """
        获取数仓知识导航（summary + 类型工具列表）

        返回字段：
        - summary: 各类型资产总数
        - navigation: 类型层级与可用工具列表（不绑定具体对象）
        """
        logger.info("get_knowledge_navigation()")

        summary = Neo4jNodeSearch.get_knowledge_navigation()
        if summary is None:
            return _tool_error("获取数仓知识导航失败")

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
