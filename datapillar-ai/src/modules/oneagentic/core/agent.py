"""
Agent 定义

核心类：
- AgentSpec: Agent 规格（声明式配置）
- AgentRegistry: Agent 注册中心（全局单例）
- @agent: 装饰器，定义即注册

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
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from pydantic import BaseModel

    from src.modules.oneagentic.a2a.config import A2AConfig

logger = logging.getLogger(__name__)


# Agent run 方法的类型签名
AgentRunFn = Callable[[Any], Awaitable[Any]]


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

    tools: list[str] = field(default_factory=list)
    """工具名称列表（框架会解析为实际工具）"""

    # === 委派配置（框架自动填充）===
    can_delegate_to: list[str] = field(default_factory=list)
    """可委派的目标 Agent ID 列表（由 Datapillar 在 DYNAMIC 模式下自动设置）"""

    # === 交付物契约 ===
    deliverable_schema: type[BaseModel] | None = None
    """交付物数据结构（Pydantic 模型，框架自动处理 LLM 结构化输出）"""

    deliverable_key: str = ""
    """交付物标识 key（如 analysis, plan，用于存储和下游获取）"""

    # === 执行配置 ===
    temperature: float = 0.0
    """LLM 温度"""

    max_iterations: int = 5
    """最大工具调用轮次"""

    # === 知识配置 ===
    knowledge_domains: list[str] = field(default_factory=list)
    """需要的知识领域 ID 列表（框架自动注入到 Context）"""

    # === 经验学习 ===
    learn: bool = True
    """是否参与经验学习（默认参与）"""

    # === A2A 远程 Agent ===
    a2a_agents: list[A2AConfig] = field(default_factory=list)
    """远程 A2A Agent 配置列表（框架自动创建委派工具）"""

    # === 运行时（框架填充）===
    run_fn: AgentRunFn | None = None
    """Agent 的 run() 方法"""


class AgentRegistry:
    """
    Agent 注册中心（全局单例）

    管理所有已注册的 Agent。

    注意：此类是框架内部使用，业务侧不应直接操作。
    """

    _agents: dict[str, AgentSpec] = {}

    @classmethod
    def register(cls, spec: AgentSpec) -> None:
        """注册 Agent"""
        if spec.id in cls._agents:
            logger.warning(f"Agent {spec.id} 已存在，将被覆盖")

        cls._agents[spec.id] = spec
        logger.info(f"📦 Agent 注册: {spec.name} ({spec.id})")

    @classmethod
    def get(cls, agent_id: str) -> AgentSpec | None:
        """获取 Agent 规格"""
        return cls._agents.get(agent_id)

    @classmethod
    def get_entry_agent(cls) -> AgentSpec | None:
        """获取入口 Agent"""
        for spec in cls._agents.values():
            if spec.is_entry:
                return spec
        return None

    @classmethod
    def list_ids(cls) -> list[str]:
        """列出所有 Agent ID"""
        return list(cls._agents.keys())

    @classmethod
    def count(cls) -> int:
        """返回 Agent 数量"""
        return len(cls._agents)

    @classmethod
    def clear(cls) -> None:
        """清空（仅测试用）"""
        cls._agents.clear()


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
            f"Agent {cls.__name__}.run() 签名错误，" f"必须是 run(self, ctx: AgentContext)"
        )

    # 第二个参数应该是 ctx
    if params[1] != "ctx":
        raise ValueError(
            f"Agent {cls.__name__}.run() 的第二个参数必须命名为 'ctx'，" f"当前是 '{params[1]}'"
        )

    # 检查是否是异步方法
    if not inspect.iscoroutinefunction(run_method):
        raise ValueError(f"Agent {cls.__name__}.run() 必须是异步方法（async def）")


def _validate_deliverable_schema(schema: type | None, class_name: str) -> None:
    """校验 deliverable_schema"""
    if schema is None:
        return

    # 检查是否是 Pydantic 模型
    from pydantic import BaseModel

    if not (isinstance(schema, type) and issubclass(schema, BaseModel)):
        raise ValueError(
            f"Agent {class_name} 的 deliverable_schema 必须是 Pydantic BaseModel 子类，"
            f"当前是 {type(schema)}"
        )


def agent(
    id: str,
    name: str,
    *,
    description: str = "",
    tools: list[str] | None = None,
    a2a_agents: list[A2AConfig] | None = None,
    deliverable_schema: type | None = None,
    deliverable_key: str = "",
    temperature: float = 0.0,
    max_iterations: int = 5,
    knowledge_domains: list[str] | None = None,
    learn: bool = True,
):
    """
    Agent 定义装饰器

    在类上使用 @agent(...) 定义一个 Agent。
    类必须实现 async def run(self, ctx: AgentContext) 方法。

    使用示例：
    ```python
    @agent(
        id="analyst",
        name="需求分析师",
        tools=["search_tables"],
        deliverable_schema=AnalysisOutput,
        deliverable_key="analysis",
    )
    class AnalystAgent:
        SYSTEM_PROMPT = "你是需求分析师..."

        async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
            messages = ctx.build_messages(self.SYSTEM_PROMPT)
            messages = await ctx.invoke_tools(messages)  # 委派由框架自动处理

            return await ctx.get_output(messages)
    ```

    参数：
    - id: Agent 唯一标识（小写字母开头，只能包含小写字母、数字、下划线）
    - name: 显示名称
    - description: 能力描述
    - tools: 工具名称列表
    - a2a_agents: 远程 A2A Agent 配置列表（跨服务调用）
    - deliverable_schema: 交付物数据结构（Pydantic 模型）
    - deliverable_key: 交付物标识 key
    - temperature: LLM 温度
    - max_iterations: 最大工具调用轮次
    - knowledge_domains: 需要的知识领域 ID 列表
    - learn: 是否参与经验学习（默认 True）

    注意：
    - 入口 Agent 由 Datapillar 的 agents 列表第一个决定
    - 委派关系由 Datapillar 在 DYNAMIC 模式下自动推断
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
                f"Agent {cls.__name__} 的 temperature 必须在 0.0-2.0 之间，" f"当前是 {temperature}"
            )

        # 5. 校验 max_iterations 范围
        if not 1 <= max_iterations <= 20:
            raise ValueError(
                f"Agent {cls.__name__} 的 max_iterations 必须在 1-20 之间，"
                f"当前是 {max_iterations}"
            )

        # === 创建实例和规格 ===

        instance = cls()

        spec = AgentSpec(
            id=id,
            name=name,
            description=description,
            tools=tools or [],
            a2a_agents=a2a_agents or [],
            deliverable_schema=deliverable_schema,
            deliverable_key=deliverable_key,
            temperature=temperature,
            max_iterations=max_iterations,
            knowledge_domains=knowledge_domains or [],
            learn=learn,
            run_fn=instance.run,
        )

        # 注册
        AgentRegistry.register(spec)

        return cls

    return decorator
