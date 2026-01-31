# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
工具注册管理中心（AI 项目侧）
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from datapillar_oneagentic import tool as base_tool


@dataclass(frozen=True, slots=True)
class ToolMeta:
    """工具元信息"""

    name: str
    tool_type: str
    desc: str


class ToolRegistry:
    """工具注册中心"""

    def __init__(self) -> None:
        self._tools: dict[str, ToolMeta] = {}

    def register(self, name: str, tool_type: str, desc: str) -> None:
        if not isinstance(name, str) or not name.strip():
            raise ValueError("工具名不能为空")
        if not isinstance(tool_type, str) or not tool_type.strip():
            raise ValueError("工具类型不能为空")
        if not isinstance(desc, str) or not desc.strip():
            raise ValueError("工具描述不能为空")
        self._tools[name.strip()] = ToolMeta(
            name=name.strip(),
            tool_type=tool_type.strip(),
            desc=desc.strip(),
        )

    def list_by_type(self, tool_type: str) -> list[ToolMeta]:
        if not tool_type or not isinstance(tool_type, str):
            return []
        items = [t for t in self._tools.values() if t.tool_type == tool_type]
        return sorted(items, key=lambda x: x.name)

    def list_all(self) -> list[ToolMeta]:
        return sorted(self._tools.values(), key=lambda x: (x.tool_type, x.name))


REGISTRY = ToolRegistry()


def etl_tool(
    name_or_func: str | Callable | None = None,
    *,
    tool_type: str,
    desc: str,
    args_schema: Any = None,
    return_direct: bool = False,
    infer_schema: bool = True,
) -> Any:
    """AI 项目侧工具装饰器：注册工具元信息 + 生成可注入工具对象"""

    def _create_tool(func: Callable, custom_name: str | None = None) -> Any:
        tool_instance = base_tool(
            custom_name,
            args_schema=args_schema,
            return_direct=return_direct,
            infer_schema=infer_schema,
        )(func)
        REGISTRY.register(tool_instance.name, tool_type, desc)
        return tool_instance

    if callable(name_or_func):
        return _create_tool(name_or_func, None)

    def decorator(func: Callable) -> Any:
        custom_name = name_or_func if isinstance(name_or_func, str) else None
        return _create_tool(func, custom_name)

    return decorator
