"""
Agent 执行器

负责执行单个 Agent：
1. 准备 AgentContext
2. 调用 Agent 的 run() 方法
3. 处理返回结果
4. 发送执行事件
"""

from __future__ import annotations

import logging
import time
from typing import Any

from langgraph.types import Command

from datapillar_oneagentic.core.agent import AgentRegistry, AgentSpec
from datapillar_oneagentic.core.context import AgentContext, DelegationSignal
from datapillar_oneagentic.core.types import AgentResult, Clarification
from datapillar_oneagentic.events import (
    event_bus,
    AgentStartedEvent,
    AgentCompletedEvent,
    AgentFailedEvent,
)
from datapillar_oneagentic.providers.llm import call_llm
from datapillar_oneagentic.tools.delegation import create_delegation_tools
from datapillar_oneagentic.tools.registry import resolve_tools
from datapillar_oneagentic.mcp.tool import MCPToolkit
from datapillar_oneagentic.a2a.tool import create_a2a_tools

logger = logging.getLogger(__name__)


class AgentExecutor:
    """
    Agent 执行器

    负责执行单个 Agent，构建 AgentContext，处理返回结果。
    """

    def __init__(self, spec: AgentSpec):
        """
        创建执行器

        参数：
        - spec: Agent 规格
        """
        self.spec = spec

        # 解析业务工具
        self.business_tools = resolve_tools(spec.tools)

        # 创建委派工具（使用正统实现）
        agent_names = {
            agent_id: AgentRegistry.get(agent_id).name
            for agent_id in (spec.can_delegate_to or [])
            if AgentRegistry.get(agent_id)
        }
        self.delegation_tools = create_delegation_tools(
            can_delegate_to=spec.can_delegate_to or [],
            agent_names=agent_names,
        )

        # MCP 工具（在 execute 中异步初始化）
        self.mcp_tools: list = []
        self._mcp_toolkit: MCPToolkit | None = None
        self._mcp_initialized = False

        # A2A 工具（在 execute 中异步初始化）
        self.a2a_tools: list = []
        self._a2a_initialized = False

        # 所有工具（不含 MCP/A2A，这些在执行时动态添加）
        self.base_tools = self.business_tools + self.delegation_tools

        # 创建 LLM（统一使用 call_llm）
        self.llm = call_llm(temperature=spec.temperature)

        logger.info(
            f"📦 Executor 创建: {spec.name} ({spec.id}), "
            f"工具: {len(self.business_tools)}, 委派: {len(self.delegation_tools)}, "
            f"MCP服务器: {len(spec.mcp_servers)}, A2A代理: {len(spec.a2a_agents)}"
        )

    async def _init_mcp_tools(self) -> None:
        """异步初始化 MCP 工具"""
        if self._mcp_initialized:
            return

        spec = self.spec
        if spec.mcp_servers:
            try:
                self._mcp_toolkit = MCPToolkit(spec.mcp_servers)
                await self._mcp_toolkit.connect()
                self.mcp_tools = self._mcp_toolkit.get_tools()
                logger.info(f"🔌 [{spec.name}] MCP 工具加载: {len(self.mcp_tools)} 个")
            except Exception as e:
                logger.error(f"🔌 [{spec.name}] MCP 工具加载失败: {e}")
                self.mcp_tools = []
                self._mcp_toolkit = None

        self._mcp_initialized = True

    async def close(self) -> None:
        """关闭资源（MCP 连接等）"""
        if self._mcp_toolkit:
            await self._mcp_toolkit.close()
            self._mcp_toolkit = None
            self.mcp_tools = []
            self._mcp_initialized = False

    async def _init_a2a_tools(self) -> None:
        """异步初始化 A2A 工具"""
        if self._a2a_initialized:
            return

        spec = self.spec
        if spec.a2a_agents:
            try:
                self.a2a_tools = await create_a2a_tools(spec.a2a_agents)
                logger.info(f"🔗 [{spec.name}] A2A 工具加载: {len(self.a2a_tools)} 个")
            except Exception as e:
                logger.error(f"🔗 [{spec.name}] A2A 工具加载失败: {e}")
                self.a2a_tools = []

        self._a2a_initialized = True

    @property
    def all_tools(self) -> list:
        """获取所有工具（包含动态加载的 MCP 和 A2A 工具）"""
        return self.base_tools + self.mcp_tools + self.a2a_tools

    async def execute(
        self,
        *,
        query: str,
        state: dict,
        experience_context: str | None = None,
    ) -> AgentResult | Command:
        """
        执行 Agent

        参数：
        - query: 用户输入
        - state: 共享状态（必须包含 namespace 和 session_id）
        - experience_context: 经验上下文（框架自动检索注入）

        返回：
        - AgentResult 或 Command（委派）

        注意：
        - Store 通过 LangGraph 的 get_store() 自动获取，无需手动传递
        - Store 在 graph.compile(store=store) 时注入
        """
        spec = self.spec
        session_id = state["session_id"]
        start_time = time.time()

        if not query:
            return AgentResult.failed(error="query 不能为空")

        # 初始化 MCP 和 A2A 工具（首次执行时）
        await self._init_mcp_tools()
        await self._init_a2a_tools()

        logger.info(f"📋 [{spec.name}] 开始执行: {query[:100]}...")

        # 发送 Agent 开始事件
        await event_bus.emit(
            self,
            AgentStartedEvent(
                agent_id=spec.id,
                agent_name=spec.name,
                session_id=session_id,
                query=query[:200],
            ),
        )

        try:
            # 获取知识 prompt（根据 Agent 声明的 knowledge_domains）
            knowledge_prompt = ""
            if spec.knowledge_domains:
                from datapillar_oneagentic.context.knowledge import KnowledgeRegistry
                knowledge_prompt = KnowledgeRegistry.get_knowledge_prompt(
                    domains=spec.knowledge_domains,
                    agent_id=spec.id,
                )

            # 构建 AgentContext（namespace 和 session_id 从 state 获取）
            ctx = AgentContext(
                namespace=state["namespace"],
                session_id=state["session_id"],
                query=query,
                _spec=spec,
                _knowledge_prompt=knowledge_prompt,
                _experience_prompt=experience_context or "",
                _llm=self.llm,
                _tools=self.all_tools,
                _state=state,
            )

            # 校验 agent_class（防呆：手动创建 AgentSpec 但未设置 agent_class）
            if spec.agent_class is None:
                raise ValueError(
                    f"Agent {spec.id} 的 agent_class 为 None。"
                    f"请使用 @agent 装饰器注册 Agent，或手动设置 AgentSpec.agent_class。"
                )

            # 调用 Agent 的 run() 方法（每次执行创建新实例，避免单例共享）
            instance = spec.agent_class()
            result = await instance.run(ctx)

            # 处理 None
            if result is None:
                await self._emit_failed_event(spec, session_id, start_time, "run() 返回 None", "NoneReturnError")
                return AgentResult.failed(error="run() 返回 None")

            # 处理 Clarification
            if isinstance(result, Clarification):
                logger.info(f"❓ [{spec.name}] 需要澄清: {result.message}")
                return AgentResult.needs_clarification(result)

            # 处理 deliverable_schema 实例（schema 必填，不会为 None）
            if isinstance(result, spec.deliverable_schema):
                logger.info(f"✅ [{spec.name}] 完成")

                # 发送 Agent 完成事件
                duration_ms = (time.time() - start_time) * 1000
                await event_bus.emit(
                    self,
                    AgentCompletedEvent(
                        agent_id=spec.id,
                        agent_name=spec.name,
                        session_id=session_id,
                        result="completed",
                        duration_ms=duration_ms,
                    ),
                )

                # 返回 AgentResult，附带 ctx._messages 供 nodes.py 写回 state
                return AgentResult.completed(
                    deliverable=result,
                    deliverable_type=spec.id,
                    messages=ctx._messages,  # 传递 Agent 执行过程中的 messages
                )

            # 类型错误
            raise TypeError(
                f"Agent {spec.id} 的 run() 返回类型错误: "
                f"期望 {spec.deliverable_schema.__name__}, "
                f"实际 {type(result).__name__}"
            )

        except DelegationSignal as signal:
            # 委派信号由框架处理
            logger.info(f"🔄 [{spec.name}] 委派给 {signal.command.goto}")
            return signal.command
        except TypeError:
            raise
        except Exception as e:
            logger.error(f"[{spec.name}] 执行失败: {e}", exc_info=True)
            await self._emit_failed_event(spec, session_id, start_time, str(e), type(e).__name__)
            return AgentResult.system_error(error=str(e))

    async def _emit_failed_event(
        self,
        spec: AgentSpec,
        session_id: str,
        start_time: float,
        error: str,
        error_type: str,
    ) -> None:
        """发送 Agent 失败事件"""
        await event_bus.emit(
            self,
            AgentFailedEvent(
                agent_id=spec.id,
                agent_name=spec.name,
                session_id=session_id,
                error=error,
                error_type=error_type,
            ),
        )
