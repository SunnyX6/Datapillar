"""
工具系统

业务侧使用：
- @tool 装饰器定义工具（自动注册）

使用示例：
```python
from src.modules.oneagentic import tool

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

# 解析 docstring
@tool(parse_docstring=True)
def search_orders(user_id: str, status: str) -> str:
    '''搜索订单

    Args:
        user_id: 用户 ID
        status: 订单状态
    '''
    return ...
```

Agent 使用工具名引用：
```python
@agent(
    id="query_agent",
    tools=["search_tables", "table_search"],  # 使用工具名
    ...
)
class QueryAgent:
    ...
```
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool
from langchain_core.tools import tool as _langchain_tool

logger = logging.getLogger(__name__)


# === 工具注册中心（框架内部）===


class ToolRegistry:
    """
    工具注册中心（框架内部使用）

    业务侧不需要直接使用此类，@tool 装饰器会自动注册。
    """

    _tools: dict[str, BaseTool] = {}

    @classmethod
    def register(cls, name: str, tool_instance: Any) -> None:
        """注册工具（框架内部）"""
        if name in cls._tools:
            logger.warning(f"工具 {name} 已存在，将被覆盖")

        cls._tools[name] = tool_instance
        logger.debug(f"🔧 工具注册: {name}")

    @classmethod
    def get(cls, name: str) -> BaseTool | None:
        """获取工具（框架内部）"""
        return cls._tools.get(name)

    @classmethod
    def resolve(cls, names: list[str]) -> list[BaseTool]:
        """解析工具名称列表（框架内部）"""
        tools = []
        for name in names:
            tool_instance = cls._tools.get(name)
            if tool_instance:
                tools.append(tool_instance)
            else:
                logger.warning(f"工具 {name} 不存在，跳过")
        return tools

    @classmethod
    def list_names(cls) -> list[str]:
        """列出所有工具名称"""
        return list(cls._tools.keys())

    @classmethod
    def count(cls) -> int:
        """返回工具数量"""
        return len(cls._tools)

    @classmethod
    def clear(cls) -> None:
        """清空（仅测试用）"""
        cls._tools.clear()


# === 对外 API：@tool 装饰器（自动注册）===


def tool(
    name_or_func: str | Callable | None = None,
    *,
    args_schema: Any = None,
    return_direct: bool = False,
    infer_schema: bool = True,
) -> Any:
    """
    工具定义装饰器（自动注册）

    功能等同于 LangChain 的 @tool，额外增加自动注册功能。

    使用示例：
    ```python
    from src.modules.oneagentic import tool

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

    # 自定义名称 + Pydantic Schema（参数描述在 Schema 中定义）
    class SearchArgs(BaseModel):
        keyword: str = Field(..., description="搜索关键词")
        limit: int = Field(10, description="返回数量")

    @tool("advanced_search", args_schema=SearchArgs)
    def search_v2(keyword: str, limit: int = 10) -> str:
        '''高级搜索'''
        return ...
    ```

    Agent 使用工具名引用：
    ```python
    @agent(
        id="query_agent",
        tools=["search_tables", "table_search", "advanced_search"],
        ...
    )
    class QueryAgent:
        ...
    ```

    参数：
    - name_or_func: 工具名称（str）或被装饰的函数
    - args_schema: 参数 Schema（Pydantic 模型），提供后参数描述从 Schema 获取
    - return_direct: 是否直接返回结果给用户（默认 False）
    - infer_schema: 从类型注解推断 Schema（默认 True）

    注意：
    - 无 args_schema 时：docstring 第一行作为描述，Args 部分作为参数描述
    - 有 args_schema 时：docstring 作为描述，参数描述从 Schema 的 Field 获取
    - 工具会自动注册到框架，无需手动操作
    """

    def _create_and_register(func: Callable, custom_name: str | None = None) -> BaseTool:
        """创建工具并自动注册"""
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

        # 自动注册
        tool_name = tool_instance.name or func.__name__
        ToolRegistry.register(tool_name, tool_instance)

        return tool_instance

    # 情况 1: @tool（无参数）
    if callable(name_or_func):
        return _create_and_register(name_or_func, None)

    # 情况 2: @tool("name") 或 @tool(parse_docstring=True, ...)
    def decorator(func: Callable) -> BaseTool:
        custom_name = name_or_func if isinstance(name_or_func, str) else None
        return _create_and_register(func, custom_name)

    return decorator


# === 便捷函数（框架内部使用）===


def resolve_tools(tool_names: list[str]) -> list[Any]:
    """解析工具名称列表（框架内部）"""
    return ToolRegistry.resolve(tool_names)
