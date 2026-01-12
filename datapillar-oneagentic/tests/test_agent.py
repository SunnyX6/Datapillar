"""
Agent 系统单元测试

测试模块：
- datapillar_oneagentic.core.agent
"""

import pytest
from pydantic import BaseModel, Field

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.core.agent import (
    AgentRegistry,
    AgentSpec,
    agent,
    _validate_id,
    _validate_run_method,
    _validate_deliverable_schema,
)


class TestAgentSpec:
    """AgentSpec 规格类测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(agent={"max_steps": 25})
        yield
        reset_config()

    def test_agent_spec_basic(self):
        """测试基础 AgentSpec 创建"""
        spec = AgentSpec(
            id="test_agent",
            name="测试 Agent",
            description="测试描述",
        )

        assert spec.id == "test_agent"
        assert spec.name == "测试 Agent"
        assert spec.description == "测试描述"

    def test_agent_spec_defaults(self):
        """测试 AgentSpec 默认值"""
        spec = AgentSpec(id="default_agent", name="默认 Agent")

        assert spec.description == ""
        assert spec.tools == []
        assert spec.can_delegate_to == []
        assert spec.deliverable_schema is None
        assert spec.deliverable_key == ""
        assert spec.temperature == 0.0
        assert spec.max_steps is None
        assert spec.knowledge_domains == []
        assert spec.a2a_agents == []
        assert spec.run_fn is None

    def test_agent_spec_with_tools(self):
        """测试带工具的 AgentSpec"""
        spec = AgentSpec(
            id="tool_agent",
            name="工具 Agent",
            tools=["search", "calculate"],
        )

        assert spec.tools == ["search", "calculate"]

    def test_agent_spec_with_deliverable(self):
        """测试带交付物的 AgentSpec"""

        class TestOutput(BaseModel):
            result: str

        spec = AgentSpec(
            id="deliverable_agent",
            name="交付物 Agent",
            deliverable_schema=TestOutput,
            deliverable_key="test_result",
        )

        assert spec.deliverable_schema == TestOutput
        assert spec.deliverable_key == "test_result"

    def test_get_max_steps_from_spec(self):
        """测试从 spec 获取 max_steps"""
        spec = AgentSpec(
            id="steps_agent",
            name="步数 Agent",
            max_steps=50,
        )

        assert spec.get_max_steps() == 50

    def test_get_max_steps_from_config(self):
        """测试从全局配置获取 max_steps"""
        spec = AgentSpec(
            id="config_steps_agent",
            name="配置步数 Agent",
            max_steps=None,
        )

        assert spec.get_max_steps() == 25


class TestAgentRegistry:
    """AgentRegistry 注册中心测试"""

    @pytest.fixture(autouse=True)
    def clear_registry(self):
        """每个测试前清空注册中心"""
        AgentRegistry.clear()
        yield
        AgentRegistry.clear()

    def test_register_agent(self):
        """测试注册 Agent"""
        spec = AgentSpec(id="reg_agent", name="注册 Agent")
        AgentRegistry.register(spec)

        assert AgentRegistry.get("reg_agent") is not None

    def test_get_agent(self):
        """测试获取 Agent"""
        spec = AgentSpec(id="get_agent", name="获取 Agent")
        AgentRegistry.register(spec)

        result = AgentRegistry.get("get_agent")

        assert result is not None
        assert result.id == "get_agent"
        assert result.name == "获取 Agent"

    def test_get_nonexistent_agent(self):
        """测试获取不存在的 Agent"""
        result = AgentRegistry.get("nonexistent")

        assert result is None

    def test_list_ids(self):
        """测试列出所有 Agent ID"""
        spec1 = AgentSpec(id="list_agent_a", name="Agent A")
        spec2 = AgentSpec(id="list_agent_b", name="Agent B")
        AgentRegistry.register(spec1)
        AgentRegistry.register(spec2)

        ids = AgentRegistry.list_ids()

        assert "list_agent_a" in ids
        assert "list_agent_b" in ids

    def test_count(self):
        """测试 Agent 数量"""
        spec1 = AgentSpec(id="count_agent_1", name="Agent 1")
        spec2 = AgentSpec(id="count_agent_2", name="Agent 2")
        AgentRegistry.register(spec1)
        AgentRegistry.register(spec2)

        assert AgentRegistry.count() == 2

    def test_clear(self):
        """测试清空注册中心"""
        spec = AgentSpec(id="clear_agent", name="清空 Agent")
        AgentRegistry.register(spec)

        assert AgentRegistry.count() > 0

        AgentRegistry.clear()

        assert AgentRegistry.count() == 0

    def test_register_overwrites_existing(self):
        """测试重复注册会覆盖"""
        spec1 = AgentSpec(id="overwrite_agent", name="原始 Agent")
        spec2 = AgentSpec(id="overwrite_agent", name="新 Agent")

        AgentRegistry.register(spec1)
        AgentRegistry.register(spec2)

        result = AgentRegistry.get("overwrite_agent")

        assert result.name == "新 Agent"


class TestValidateId:
    """_validate_id() 函数测试"""

    def test_valid_id(self):
        """测试有效 ID"""
        _validate_id("analyst", "AnalystAgent")
        _validate_id("query_agent", "QueryAgent")
        _validate_id("agent123", "Agent123")
        _validate_id("a", "A")

    def test_empty_id(self):
        """测试空 ID"""
        with pytest.raises(ValueError) as exc_info:
            _validate_id("", "TestAgent")

        assert "不能为空" in str(exc_info.value)

    def test_id_starts_with_number(self):
        """测试以数字开头的 ID"""
        with pytest.raises(ValueError) as exc_info:
            _validate_id("123agent", "TestAgent")

        assert "格式错误" in str(exc_info.value)

    def test_id_with_uppercase(self):
        """测试包含大写字母的 ID"""
        with pytest.raises(ValueError) as exc_info:
            _validate_id("TestAgent", "TestAgent")

        assert "格式错误" in str(exc_info.value)

    def test_id_with_special_chars(self):
        """测试包含特殊字符的 ID"""
        with pytest.raises(ValueError) as exc_info:
            _validate_id("test-agent", "TestAgent")

        assert "格式错误" in str(exc_info.value)


class TestValidateRunMethod:
    """_validate_run_method() 函数测试"""

    def test_valid_run_method(self):
        """测试有效的 run 方法"""

        class ValidAgent:
            async def run(self, ctx):
                pass

        _validate_run_method(ValidAgent)

    def test_missing_run_method(self):
        """测试缺少 run 方法"""

        class NoRunAgent:
            pass

        with pytest.raises(ValueError) as exc_info:
            _validate_run_method(NoRunAgent)

        assert "必须实现 run" in str(exc_info.value)

    def test_sync_run_method(self):
        """测试同步 run 方法"""

        class SyncRunAgent:
            def run(self, ctx):
                pass

        with pytest.raises(ValueError) as exc_info:
            _validate_run_method(SyncRunAgent)

        assert "必须是异步方法" in str(exc_info.value)

    def test_wrong_param_name(self):
        """测试错误的参数名"""

        class WrongParamAgent:
            async def run(self, context):
                pass

        with pytest.raises(ValueError) as exc_info:
            _validate_run_method(WrongParamAgent)

        assert "必须命名为 'ctx'" in str(exc_info.value)

    def test_missing_ctx_param(self):
        """测试缺少 ctx 参数"""

        class NoCtxAgent:
            async def run(self):
                pass

        with pytest.raises(ValueError) as exc_info:
            _validate_run_method(NoCtxAgent)

        assert "签名错误" in str(exc_info.value)


class TestValidateDeliverableSchema:
    """_validate_deliverable_schema() 函数测试"""

    def test_valid_schema(self):
        """测试有效的 schema"""

        class ValidSchema(BaseModel):
            result: str

        _validate_deliverable_schema(ValidSchema, "TestAgent")

    def test_none_schema(self):
        """测试 None schema"""
        _validate_deliverable_schema(None, "TestAgent")

    def test_invalid_schema(self):
        """测试无效的 schema"""
        with pytest.raises(ValueError) as exc_info:
            _validate_deliverable_schema(dict, "TestAgent")

        assert "必须是 Pydantic BaseModel 子类" in str(exc_info.value)


class TestAgentDecorator:
    """@agent 装饰器测试"""

    @pytest.fixture(autouse=True)
    def clear_registry(self):
        """每个测试前清空注册中心"""
        AgentRegistry.clear()
        yield
        AgentRegistry.clear()

    def test_agent_decorator_basic(self):
        """测试基础装饰器用法"""

        @agent(id="basic_agent", name="基础 Agent")
        class BasicAgent:
            async def run(self, ctx):
                return "result"

        spec = AgentRegistry.get("basic_agent")

        assert spec is not None
        assert spec.id == "basic_agent"
        assert spec.name == "基础 Agent"

    def test_agent_decorator_with_tools(self):
        """测试带工具的装饰器"""

        @agent(
            id="tools_agent",
            name="工具 Agent",
            tools=["search", "calculate"],
        )
        class ToolsAgent:
            async def run(self, ctx):
                return "result"

        spec = AgentRegistry.get("tools_agent")

        assert spec.tools == ["search", "calculate"]

    def test_agent_decorator_with_deliverable(self):
        """测试带交付物的装饰器"""

        class AgentOutput(BaseModel):
            summary: str = Field(description="摘要")

        @agent(
            id="deliverable_agent",
            name="交付物 Agent",
            deliverable_schema=AgentOutput,
            deliverable_key="output",
        )
        class DeliverableAgent:
            async def run(self, ctx):
                return AgentOutput(summary="test")

        spec = AgentRegistry.get("deliverable_agent")

        assert spec.deliverable_schema == AgentOutput
        assert spec.deliverable_key == "output"

    def test_agent_decorator_with_temperature(self):
        """测试带温度的装饰器"""

        @agent(
            id="temp_agent",
            name="温度 Agent",
            temperature=0.7,
        )
        class TempAgent:
            async def run(self, ctx):
                return "result"

        spec = AgentRegistry.get("temp_agent")

        assert spec.temperature == 0.7

    def test_agent_decorator_invalid_temperature(self):
        """测试无效温度"""
        with pytest.raises(ValueError) as exc_info:

            @agent(
                id="invalid_temp",
                name="无效温度",
                temperature=3.0,
            )
            class InvalidTempAgent:
                async def run(self, ctx):
                    return "result"

        assert "temperature 必须在 0.0-2.0 之间" in str(exc_info.value)

    def test_agent_decorator_with_max_steps(self):
        """测试带 max_steps 的装饰器"""

        @agent(
            id="steps_agent",
            name="步数 Agent",
            max_steps=100,
        )
        class StepsAgent:
            async def run(self, ctx):
                return "result"

        spec = AgentRegistry.get("steps_agent")

        assert spec.max_steps == 100

    def test_agent_decorator_with_knowledge_domains(self):
        """测试带知识领域的装饰器"""

        @agent(
            id="knowledge_agent",
            name="知识 Agent",
            knowledge_domains=["etl", "sql"],
        )
        class KnowledgeAgent:
            async def run(self, ctx):
                return "result"

        spec = AgentRegistry.get("knowledge_agent")

        assert spec.knowledge_domains == ["etl", "sql"]

    def test_agent_decorator_stores_run_fn(self):
        """测试装饰器存储 run 方法"""

        @agent(id="run_fn_agent", name="运行函数 Agent")
        class RunFnAgent:
            async def run(self, ctx):
                return "result"

        spec = AgentRegistry.get("run_fn_agent")

        assert spec.run_fn is not None
        assert callable(spec.run_fn)

    def test_agent_decorator_invalid_id(self):
        """测试无效 ID"""
        with pytest.raises(ValueError) as exc_info:

            @agent(id="InvalidId", name="无效ID")
            class InvalidIdAgent:
                async def run(self, ctx):
                    return "result"

        assert "格式错误" in str(exc_info.value)

    def test_agent_decorator_missing_run(self):
        """测试缺少 run 方法"""
        with pytest.raises(ValueError) as exc_info:

            @agent(id="no_run", name="无运行方法")
            class NoRunAgent:
                pass

        assert "必须实现 run" in str(exc_info.value)
