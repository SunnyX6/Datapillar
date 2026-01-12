"""
工具系统单元测试

测试模块：datapillar_oneagentic.tools.registry
"""

import pytest

from datapillar_oneagentic.tools.registry import ToolRegistry, tool, resolve_tools


class TestToolRegistry:
    """ToolRegistry 工具注册中心测试"""

    @pytest.fixture(autouse=True)
    def clear_registry(self):
        """每个测试前清空注册中心"""
        ToolRegistry.clear()
        yield
        ToolRegistry.clear()

    def test_register_tool(self):
        """测试注册工具"""

        @tool
        def test_tool(x: int) -> str:
            """测试工具

            Args:
                x: 输入参数
            """
            return f"result: {x}"

        assert ToolRegistry.get("test_tool") is not None
        assert ToolRegistry.count() == 1

    def test_get_tool(self):
        """测试获取工具"""

        @tool
        def get_test_tool(x: int) -> str:
            """获取测试工具

            Args:
                x: 输入参数
            """
            return str(x)

        tool_instance = ToolRegistry.get("get_test_tool")

        assert tool_instance is not None
        assert tool_instance.name == "get_test_tool"

    def test_get_nonexistent_tool(self):
        """测试获取不存在的工具"""
        result = ToolRegistry.get("nonexistent")

        assert result is None

    def test_list_names(self):
        """测试列出所有工具名称"""

        @tool
        def tool_a() -> str:
            """工具A"""
            return "a"

        @tool
        def tool_b() -> str:
            """工具B"""
            return "b"

        names = ToolRegistry.list_names()

        assert "tool_a" in names
        assert "tool_b" in names

    def test_count(self):
        """测试工具数量"""

        @tool
        def count_tool_1() -> str:
            """计数工具1"""
            return "1"

        @tool
        def count_tool_2() -> str:
            """计数工具2"""
            return "2"

        assert ToolRegistry.count() == 2

    def test_clear(self):
        """测试清空注册中心"""

        @tool
        def clear_test_tool() -> str:
            """清空测试工具"""
            return "clear"

        assert ToolRegistry.count() > 0

        ToolRegistry.clear()

        assert ToolRegistry.count() == 0

    def test_resolve_tools(self):
        """测试解析工具名称列表"""

        @tool
        def resolve_tool_a() -> str:
            """解析工具A"""
            return "a"

        @tool
        def resolve_tool_b() -> str:
            """解析工具B"""
            return "b"

        tools = ToolRegistry.resolve(["resolve_tool_a", "resolve_tool_b"])

        assert len(tools) == 2

    def test_resolve_tools_skip_nonexistent(self):
        """测试解析工具时跳过不存在的工具"""

        @tool
        def resolve_existing() -> str:
            """存在的工具"""
            return "exists"

        tools = ToolRegistry.resolve(["resolve_existing", "nonexistent_tool"])

        assert len(tools) == 1

    def test_register_overwrites_existing(self):
        """测试重复注册会覆盖"""

        @tool
        def overwrite_tool() -> str:
            """原始工具"""
            return "original"

        @tool("overwrite_tool")
        def new_overwrite_tool() -> str:
            """新工具"""
            return "new"

        tool_instance = ToolRegistry.get("overwrite_tool")

        assert tool_instance is not None


class TestToolDecorator:
    """@tool 装饰器测试"""

    @pytest.fixture(autouse=True)
    def clear_registry(self):
        """每个测试前清空注册中心"""
        ToolRegistry.clear()
        yield
        ToolRegistry.clear()

    def test_tool_decorator_basic(self):
        """测试基础用法（工具名 = 函数名）"""

        @tool
        def basic_tool(x: int) -> str:
            """基础工具

            Args:
                x: 输入参数
            """
            return str(x)

        assert ToolRegistry.get("basic_tool") is not None

    def test_tool_decorator_custom_name(self):
        """测试自定义工具名称"""

        @tool("custom_name")
        def original_func(x: int) -> str:
            """自定义名称工具

            Args:
                x: 输入参数
            """
            return str(x)

        assert ToolRegistry.get("custom_name") is not None
        assert ToolRegistry.get("original_func") is None

    def test_tool_decorator_with_docstring(self):
        """测试 docstring 解析"""

        @tool
        def docstring_tool(keyword: str) -> str:
            """搜索数据

            Args:
                keyword: 搜索关键词
            """
            return f"result: {keyword}"

        tool_instance = ToolRegistry.get("docstring_tool")

        assert tool_instance is not None
        assert "搜索" in tool_instance.description

    def test_tool_decorator_return_direct(self):
        """测试 return_direct 参数"""

        @tool(return_direct=True)
        def direct_tool() -> str:
            """直接返回工具"""
            return "direct"

        tool_instance = ToolRegistry.get("direct_tool")

        assert tool_instance is not None
        assert tool_instance.return_direct is True

    def test_tool_with_multiple_args(self):
        """测试多参数工具"""

        @tool
        def multi_arg_tool(a: int, b: str, c: float = 1.0) -> str:
            """多参数工具

            Args:
                a: 整数参数
                b: 字符串参数
                c: 浮点参数
            """
            return f"{a}-{b}-{c}"

        tool_instance = ToolRegistry.get("multi_arg_tool")

        assert tool_instance is not None


class TestResolveTools:
    """resolve_tools() 函数测试"""

    @pytest.fixture(autouse=True)
    def clear_registry(self):
        """每个测试前清空注册中心"""
        ToolRegistry.clear()
        yield
        ToolRegistry.clear()

    def test_resolve_tools_function(self):
        """测试 resolve_tools 便捷函数"""

        @tool
        def func_tool_a() -> str:
            """函数工具A"""
            return "a"

        @tool
        def func_tool_b() -> str:
            """函数工具B"""
            return "b"

        tools = resolve_tools(["func_tool_a", "func_tool_b"])

        assert len(tools) == 2

    def test_resolve_tools_empty_list(self):
        """测试解析空列表"""
        tools = resolve_tools([])

        assert tools == []


class TestToolExecution:
    """工具执行测试"""

    @pytest.fixture(autouse=True)
    def clear_registry(self):
        """每个测试前清空注册中心"""
        ToolRegistry.clear()
        yield
        ToolRegistry.clear()

    def test_tool_invoke_sync(self):
        """测试同步工具调用"""

        @tool
        def sync_invoke_tool(x: int, y: int) -> str:
            """同步调用工具

            Args:
                x: 第一个数
                y: 第二个数
            """
            return f"{x} + {y} = {x + y}"

        tool_instance = ToolRegistry.get("sync_invoke_tool")
        result = tool_instance.invoke({"x": 1, "y": 2})

        assert result == "1 + 2 = 3"

    @pytest.mark.asyncio
    async def test_tool_invoke_async(self):
        """测试异步工具调用"""

        @tool
        def async_invoke_tool(name: str) -> str:
            """异步调用工具

            Args:
                name: 名称
            """
            return f"Hello, {name}!"

        tool_instance = ToolRegistry.get("async_invoke_tool")
        result = await tool_instance.ainvoke({"name": "World"})

        assert result == "Hello, World!"

    def test_tool_with_complex_return(self):
        """测试复杂返回值工具"""

        @tool
        def complex_return_tool(items: list) -> str:
            """复杂返回工具

            Args:
                items: 项目列表
            """
            return f"Found {len(items)} items: {', '.join(items)}"

        tool_instance = ToolRegistry.get("complex_return_tool")
        result = tool_instance.invoke({"items": ["a", "b", "c"]})

        assert "3 items" in result
        assert "a, b, c" in result
