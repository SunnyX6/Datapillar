# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Tool system.

Application usage:
- Use the @tool decorator to define tools (returns an injectable tool object)

Example:
```python
from datapillar_oneagentic import tool

# Basic usage (auto-register, tool name = function name)
@tool
def search_tables(keyword: str) -> str:
    '''Search tables in the data catalog.

    Args:
        keyword: Search keyword
    '''
    return f"Found tables: users, orders (keyword: {keyword})"

# Custom name
@tool("table_search")
def search(keyword: str) -> str:
    '''Search tables.'''
    return ...
```

Agent uses tool objects:
```python
@agent(
    id="query_agent",
    tools=[search_tables, search],
    ...
)
class QueryAgent:
    ...
```
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool
from langchain_core.tools import tool as _langchain_tool


# === Public API: @tool decorator ===


def tool(
    name_or_func: str | Callable | None = None,
    *,
    args_schema: Any = None,
    return_direct: bool = False,
    infer_schema: bool = True,
) -> Any:
    """
    Tool definition decorator.

    Equivalent to LangChain's @tool and returns an injectable tool object.

    Example:
    ```python
    from datapillar_oneagentic import tool

    # Basic usage (tool name = function name, docstring parsed automatically)
    @tool
    def search_tables(keyword: str) -> str:
        '''Search tables in the data catalog.

        Args:
            keyword: Search keyword
        '''
        return "Found tables: users, orders"

    # Custom tool name
    @tool("table_search")
    def search(keyword: str) -> str:
        '''Search tables.

        Args:
            keyword: Search keyword
        '''
        return ...
    ```

    Args:
        name_or_func: Tool name (str) or the decorated function
        args_schema: Argument schema (Pydantic model)
        return_direct: Whether to return results directly to the user (default False)
        infer_schema: Infer schema from type hints (default True)
    """

    def _create_tool(func: Callable, custom_name: str | None = None) -> BaseTool:
        """Create a tool."""
        # If args_schema is provided, skip docstring parsing.
        parse_docstring = args_schema is None

        if custom_name:
            tool_instance = _langchain_tool(
                custom_name,
                args_schema=args_schema,
                return_direct=return_direct,
                parse_docstring=parse_docstring,
                infer_schema=infer_schema,
            )(func)
        else:
            tool_instance = _langchain_tool(
                func,
                args_schema=args_schema,
                return_direct=return_direct,
                parse_docstring=parse_docstring,
                infer_schema=infer_schema,
            )

        return tool_instance

    # Case 1: @tool (no args)
    if callable(name_or_func):
        return _create_tool(name_or_func, None)

    # Case 2: @tool("name") or @tool(args_schema=..., ...)
    def decorator(func: Callable) -> BaseTool:
        custom_name = name_or_func if isinstance(name_or_func, str) else None
        return _create_tool(func, custom_name)

    return decorator
