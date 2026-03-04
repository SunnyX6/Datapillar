# @author Sunny
# @date 2026-01-27

"""
Neo4j Cypher Execution entry(Unified closing)

target:- unify Neo4j Query execution entry,Facilitate subsequent logging,audit,timeout,Try again,Indicators and other cross-cutting capabilities
- put `LiteralString | Query` Type constraints and dynamics of Cypher Decoupling of reality,avoid scattering `cast(...)`

Note:- `cast` Only affects static type checking,Does not affect runtime behavior
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
    Synchronous execution Cypher(Neo4j sync Session)

    Args:session:Neo4j Session(sync)
    query:Cypher string(Allow dynamic construction)
    parameters:parameter dictionary(Optional)
    **kwargs:extra parameters(will be merged into parameters)
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
    Asynchronous execution Cypher(Neo4j AsyncSession)

    Args:session:Neo4j AsyncSession(asynchronous)
    query:Cypher string(Allow dynamic construction)
    parameters:parameter dictionary(Optional)
    **kwargs:extra parameters(will be merged into parameters)
    """
    params = _merge_params(parameters, kwargs)
    if params is None:
        return await session.run(cast(Any, query))
    return await session.run(cast(Any, query), params)
