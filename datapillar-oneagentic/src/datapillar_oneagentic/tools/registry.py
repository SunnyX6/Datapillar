"""
工具系统

业务侧使用：
- @tool 装饰器定义工具（返回可注入的工具对象）

使用示例：
```python
from datapillar_oneagentic import tool

# 基础用法（自动注册，工具名 = 函数名）
@tool
def search_tables(keyword: str) -> str:
    '''搜索数据目录中的表

    Args:
        keyword: 搜索关键词
    '''
    return f"找到表: users, orders (关键词: {keyword})"

# 自定义名称
@tool("table_search")
def search(keyword: str) -> str:
    '''搜索表'''
    return ...
```

Agent 使用工具对象：
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


# === 对外 API：@tool 装饰器 ===


def tool(
    name_or_func: str | Callable | None = None,
    *,
    args_schema: Any = None,
    return_direct: bool = False,
    infer_schema: bool = True,
) -> Any:
    """
    工具定义装饰器

    功能等同于 LangChain 的 @tool，返回可注入的工具对象。

    使用示例：
    ```python
    from datapillar_oneagentic import tool

    # 基础用法（工具名 = 函数名，docstring 自动解析）
    @tool
    def search_tables(keyword: str) -> str:
        '''搜索数据目录中的表

        Args:
            keyword: 搜索关键词
        '''
        return f"找到表: users, orders"

    # 自定义工具名称
    @tool("table_search")
    def search(keyword: str) -> str:
        '''搜索表

        Args:
            keyword: 搜索关键词
        '''
        return ...
    ```

    参数：
    - name_or_func: 工具名称（str）或被装饰的函数
    - args_schema: 参数 Schema（Pydantic 模型）
    - return_direct: 是否直接返回结果给用户（默认 False）
    - infer_schema: 从类型注解推断 Schema（默认 True）
    """

    def _create_tool(func: Callable, custom_name: str | None = None) -> BaseTool:
        """创建工具"""
        # 自动判断：有 args_schema 就不解析 docstring，没有就解析
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

    # 情况 1: @tool（无参数）
    if callable(name_or_func):
        return _create_tool(name_or_func, None)

    # 情况 2: @tool("name") 或 @tool(args_schema=..., ...)
    def decorator(func: Callable) -> BaseTool:
        custom_name = name_or_func if isinstance(name_or_func, str) else None
        return _create_tool(func, custom_name)

    return decorator
