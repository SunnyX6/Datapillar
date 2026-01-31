# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Neo4j Cypher 执行入口（统一收口）

目标：
- 统一 Neo4j 查询执行入口，便于后续做日志、审计、超时、重试、指标等横切能力
- 把 `LiteralString | Query` 的类型约束与动态 Cypher 的现实情况解耦，避免到处散落 `cast(...)`

注意：
- `cast` 只影响静态类型检查，不影响运行时行为
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any, cast


def _merge_params(
    parameters: Mapping[str, Any] | None,
    kwargs: dict[str, Any],
) -> dict[str, Any] | None:
    if parameters is None:
        return kwargs or None
    merged = dict(parameters)
    if kwargs:
        merged.update(kwargs)
    return merged


def run_cypher(
    session: Any,
    query: str,
    parameters: Mapping[str, Any] | None = None,
    **kwargs: Any,
) -> Any:
    """
    同步执行 Cypher（Neo4j sync Session）

    Args:
        session: Neo4j Session（同步）
        query: Cypher 字符串（允许动态构造）
        parameters: 参数字典（可选）
        **kwargs: 额外参数（将合并进 parameters）
    """
    params = _merge_params(parameters, kwargs)
    if params is None:
        return session.run(cast(Any, query))
    return session.run(cast(Any, query), params)


async def arun_cypher(
    session: Any,
    query: str,
    parameters: Mapping[str, Any] | None = None,
    **kwargs: Any,
) -> Any:
    """
    异步执行 Cypher（Neo4j AsyncSession）

    Args:
        session: Neo4j AsyncSession（异步）
        query: Cypher 字符串（允许动态构造）
        parameters: 参数字典（可选）
        **kwargs: 额外参数（将合并进 parameters）
    """
    params = _merge_params(parameters, kwargs)
    if params is None:
        return await session.run(cast(Any, query))
    return await session.run(cast(Any, query), params)
