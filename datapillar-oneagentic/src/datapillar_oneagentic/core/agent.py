"""
Agent 定义

核心类：
- AgentSpec: Agent 规格（声明式配置）
- @agent: 装饰器，定义即绑定规格

设计原则：
- 声明式配置是契约
- 框架根据配置自动处理
- Agent 只需实现 run() 方法
- 装饰器严格校验，防止错误配置
"""

from __future__ import annotations

import inspect
import logging
import re
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from pydantic import BaseModel

    from datapillar_oneagentic.a2a.config import A2AConfig
    from datapillar_oneagentic.core.config import AgentConfig, AgentRetryConfig
    from datapillar_oneagentic.knowledge import Knowledge
    from datapillar_oneagentic.mcp.config import MCPServerConfig

logger = logging.getLogger(__name__)


@dataclass
class AgentSpec:
    """
    Agent 规格（声明式配置）

    定义 Agent 的身份、能力、输出契约。
    框架根据此规格自动处理工具注入、委派、结果构建等。

    注意：此类是框架内部使用，业务侧通过 @agent 装饰器声明。
    """

    # === 身份 ===
    id: str
    """Agent 唯一标识"""

    name: str
    """Agent 显示名称"""

    # === 能力声明 ===
    description: str = ""
    """一句话描述 Agent 能做什么"""

    tools: list[Any] = field(default_factory=list)
    """工具对象列表（BaseTool 或兼容工具）"""

    # === 委派配置（框架自动填充）===
    can_delegate_to: list[str] = field(default_factory=list)
    """可委派的目标 Agent ID 列表（由 Team 在 DYNAMIC 模式下自动设置）"""

    # === 交付物契约 ===
    deliverable_schema: type[BaseModel] | None = None
    """交付物数据结构（Pydantic 模型，框架自动处理 LLM 结构化输出）"""

    # === 执行配置 ===
    temperature: float = 0.0
    """LLM 温度"""

    max_steps: int | None = None
    """Agent 最大执行步数（None 时读团队 AgentConfig.max_steps）"""

    retry_config: "AgentRetryConfig | None" = None
    """Agent 重试配置（None 时读团队 AgentConfig.retry）"""

    timeout_seconds: float | None = None
    """Agent 单次执行超时（None 时读团队 AgentConfig.timeout_seconds）"""

    tool_timeout_seconds: float | None = None
    """工具单次调用超时（None 时读团队 AgentConfig.tool_timeout_seconds）"""

    def get_max_steps(self, config: AgentConfig) -> int:
        """获取最大执行步数"""
        return self.max_steps if self.max_steps is not None else config.max_steps

    def get_timeout_seconds(self, config: AgentConfig) -> float:
        """获取 Agent 执行超时（秒）"""
        return self.timeout_seconds if self.timeout_seconds is not None else config.timeout_seconds

    def get_retry_config(self, config: AgentConfig) -> "AgentRetryConfig":
        """获取 Agent 重试配置"""
        return self.retry_config if self.retry_config is not None else config.retry

    def get_tool_timeout_seconds(self, config: AgentConfig) -> float:
        """获取工具调用超时（秒）"""
        return self.tool_timeout_seconds if self.tool_timeout_seconds is not None else config.tool_timeout_seconds

    # === 知识配置 ===
    knowledge: "Knowledge | None" = None
    """知识配置（RAG 检索注入）"""

    # === A2A 远程 Agent ===
    a2a_agents: list[A2AConfig] = field(default_factory=list)
    """远程 A2A Agent 配置列表（框架自动创建委派工具）"""

    # === MCP 服务器 ===
    mcp_servers: list[MCPServerConfig] = field(default_factory=list)
    """MCP 服务器配置列表（框架自动将 MCP 工具转换为 Agent 可调用的工具）"""

    # === 运行时（框架填充）===
    agent_class: type | None = None
    """Agent 类引用（执行时按需创建实例，避免单例共享）"""


class AgentRegistry:
    """
    Agent 注册中心（预留接口）

    当前框架通过 @agent 装饰器绑定 AgentSpec，未在 Datapillar 解析流程中使用。
    该 Registry 仅用于扩展或测试场景。
    """

    def __init__(self) -> None:
        self._agents: dict[str, AgentSpec] = {}

    def register(self, spec: AgentSpec) -> None:
        """注册 Agent"""
        if spec.id in self._agents:
            logger.warning(f"Agent {spec.id} 已存在，将被覆盖")

        self._agents[spec.id] = spec
        logger.info(f"📦 Agent 注册: {spec.name} ({spec.id})")

    def get(self, agent_id: str) -> AgentSpec | None:
        """获取 Agent 规格"""
        return self._agents.get(agent_id)

    def list_ids(self) -> list[str]:
        """列出所有 Agent ID"""
        return list(self._agents.keys())

    def list_specs(self) -> list[AgentSpec]:
        """列出所有 Agent 规格"""
        return list(self._agents.values())

    def count(self) -> int:
        """返回 Agent 数量"""
        return len(self._agents)


# === ID 格式校验 ===
_ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


def _validate_id(agent_id: str, class_name: str) -> None:
    """校验 Agent ID 格式"""
    if not agent_id:
        raise ValueError(f"Agent {class_name} 的 id 不能为空")

    if not _ID_PATTERN.match(agent_id):
        raise ValueError(
            f"Agent {class_name} 的 id '{agent_id}' 格式错误，"
            f"必须以小写字母开头，只能包含小写字母、数字和下划线"
        )


def _validate_run_method(cls: type) -> None:
    """校验 run 方法"""
    if not hasattr(cls, "run"):
        raise ValueError(f"Agent {cls.__name__} 必须实现 run(self, ctx) 方法")

    run_method = cls.run

    # 检查是否是方法
    if not callable(run_method):
        raise ValueError(f"Agent {cls.__name__}.run 必须是方法")

    # 检查签名
    sig = inspect.signature(run_method)
    params = list(sig.parameters.keys())

    # 至少有 self 和 ctx 两个参数
    if len(params) < 2:
        raise ValueError(
            f"Agent {cls.__name__}.run() 签名错误，"
            f"必须是 run(self, ctx: AgentContext)"
        )

    # 第二个参数应该是 ctx
    if params[1] != "ctx":
        raise ValueError(
            f"Agent {cls.__name__}.run() 的第二个参数必须命名为 'ctx'，"
            f"当前是 '{params[1]}'"
        )

    # 检查是否是异步方法
    if not inspect.iscoroutinefunction(run_method):
        raise ValueError(
            f"Agent {cls.__name__}.run() 必须是异步方法（async def）"
        )


def _validate_deliverable_schema(schema: type | None, class_name: str) -> None:
    """校验 deliverable_schema（必填）"""
    if schema is None:
        raise ValueError(
            f"Agent {class_name} 必须声明 deliverable_schema，"
            f"框架统一使用结构化 JSON 输出"
        )

    # 检查是否是 Pydantic 模型
    from pydantic import BaseModel

    if not (isinstance(schema, type) and issubclass(schema, BaseModel)):
        raise ValueError(
            f"Agent {class_name} 的 deliverable_schema 必须是 Pydantic BaseModel 子类，"
            f"当前是 {type(schema)}"
        )


_AGENT_SPEC_ATTR = "__datapillar_spec__"


def agent(
    id: str,
    name: str,
    *,
    deliverable_schema: type,
    description: str = "",
    tools: list[Any] | None = None,
    mcp_servers: list[MCPServerConfig] | None = None,
    a2a_agents: list[A2AConfig] | None = None,
    temperature: float = 0.0,
    max_steps: int | None = None,
    retry_config: "AgentRetryConfig | None" = None,
    knowledge: "Knowledge | None" = None,
):
    """
    Agent 定义装饰器

    在类上使用 @agent(...) 定义一个 Agent。
    类必须实现 async def run(self, ctx: AgentContext) 方法。

    使用示例：
    ```python
    from datapillar_oneagentic.mcp import MCPServerStdio

    @agent(
        id="analyst",
        name="需求分析师",
        deliverable_schema=AnalysisOutput,
        tools=[search_tables],
        mcp_servers=[
            MCPServerStdio(
                command="npx",
                args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
            ),
        ],
    )
    class AnalystAgent:
        SYSTEM_PROMPT = "你是需求分析师..."

        async def run(self, ctx: AgentContext) -> AnalysisOutput:
            messages = ctx.build_messages(self.SYSTEM_PROMPT)
            messages = await ctx.invoke_tools(messages)

            return await ctx.get_structured_output(messages)
    ```

    参数：
    - id: Agent 唯一标识（小写字母开头，只能包含小写字母、数字、下划线）
    - name: 显示名称
    - deliverable_schema: 交付物数据结构（Pydantic 模型，必填）
    - description: 能力描述
    - tools: 工具对象列表（BaseTool 或兼容工具）
    - mcp_servers: MCP 服务器配置列表（框架自动将 MCP 工具转换为可调用工具）
    - a2a_agents: 远程 A2A Agent 配置列表（跨服务调用）
    - temperature: LLM 温度
    - max_steps: Agent 最大执行步数（None 时读团队 AgentConfig.max_steps）
    - retry_config: Agent 重试配置（None 时读团队 AgentConfig.retry）
    - knowledge: 知识配置（RAG 检索注入）
    注意：
    - 入口 Agent 由 Team 的 agents 列表第一个决定
    - 委派关系由 Team 在 DYNAMIC 模式下自动推断
    - 经验学习由 Datapillar(enable_learning=True) 统一控制
    - 交付物统一用 agent_id 存储和获取
    """

    def decorator(cls: type) -> type:
        # === 严格校验 ===

        # 1. 校验 ID 格式
        _validate_id(id, cls.__name__)

        # 2. 校验 run 方法
        _validate_run_method(cls)

        # 3. 校验 deliverable_schema
        _validate_deliverable_schema(deliverable_schema, cls.__name__)

        # 4. 校验 temperature 范围
        if not 0.0 <= temperature <= 2.0:
            raise ValueError(
                f"Agent {cls.__name__} 的 temperature 必须在 0.0-2.0 之间，"
                f"当前是 {temperature}"
            )

        # === 保存类引用（执行时按需创建实例）===

        spec = AgentSpec(
            id=id,
            name=name,
            description=description,
            tools=tools or [],
            mcp_servers=mcp_servers or [],
            a2a_agents=a2a_agents or [],
            deliverable_schema=deliverable_schema,
            temperature=temperature,
            max_steps=max_steps,
            retry_config=retry_config,
            knowledge=knowledge,
            agent_class=cls,
        )

        # 绑定规格到类
        setattr(cls, _AGENT_SPEC_ATTR, spec)

        return cls

    return decorator


def get_agent_spec(agent_class: type) -> AgentSpec | None:
    """获取 Agent 类绑定的规格"""
    return getattr(agent_class, _AGENT_SPEC_ATTR, None)
