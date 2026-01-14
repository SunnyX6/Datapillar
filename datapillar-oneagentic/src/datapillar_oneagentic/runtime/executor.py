"""
Agent 执行器

负责执行单个 Agent：
1. 准备 AgentContext
2. 调用 Agent 的 run() 方法
3. 处理返回结果
4. 发送执行事件
"""

from __future__ import annotations

import asyncio
import logging
import time

from langgraph.types import Command

from datapillar_oneagentic.a2a.tool import create_a2a_tools
from datapillar_oneagentic.context.compaction import get_compactor
from datapillar_oneagentic.core.agent import AgentRegistry, AgentSpec
from datapillar_oneagentic.core.context import AgentContext, DelegationSignal
from datapillar_oneagentic.core.types import AgentResult, SessionKey
from datapillar_oneagentic.events import (
    AgentCompletedEvent,
    AgentFailedEvent,
    AgentStartedEvent,
    event_bus,
)
from datapillar_oneagentic.mcp.tool import MCPToolkit
from datapillar_oneagentic.providers.llm import call_llm
from datapillar_oneagentic.resilience import ContextLengthExceededError
from datapillar_oneagentic.tools.delegation import create_delegation_tools
from datapillar_oneagentic.tools.registry import resolve_tools

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

        # 基础工具（不含 MCP/A2A，这些在执行时动态加载）
        self.base_tools = self.business_tools + self.delegation_tools

        # 创建 LLM（统一使用 call_llm）
        self.llm = call_llm(temperature=spec.temperature)

        logger.info(
            f"📦 Executor 创建: {spec.name} ({spec.id}), "
            f"工具: {len(self.business_tools)}, 委派: {len(self.delegation_tools)}, "
            f"MCP服务器: {len(spec.mcp_servers)}, A2A代理: {len(spec.a2a_agents)}"
        )

    async def _load_mcp_tools(self) -> tuple[list, MCPToolkit | None]:
        """加载 MCP 工具（短连接，返回工具列表和 toolkit 引用）"""
        spec = self.spec
        if not spec.mcp_servers:
            return [], None

        try:
            toolkit = MCPToolkit(spec.mcp_servers)
            await toolkit.connect()
            tools = toolkit.get_tools()
            logger.info(f"🔌 [{spec.name}] MCP 工具加载: {len(tools)} 个")
            return tools, toolkit
        except Exception as e:
            logger.error(f"🔌 [{spec.name}] MCP 工具加载失败: {e}")
            return [], None

    async def _load_a2a_tools(self) -> list:
        """加载 A2A 工具"""
        spec = self.spec
        if not spec.a2a_agents:
            return []

        try:
            tools = await create_a2a_tools(spec.a2a_agents)
            logger.info(f"🔗 [{spec.name}] A2A 工具加载: {len(tools)} 个")
            return tools
        except Exception as e:
            logger.error(f"🔗 [{spec.name}] A2A 工具加载失败: {e}")
            return []

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
        - MCP 工具采用短连接模式：执行时连接，执行完关闭
        - Store 通过 LangGraph 的 get_store() 自动获取，无需手动传递
        """
        spec = self.spec
        key = SessionKey(namespace=state["namespace"], session_id=state["session_id"])
        start_time = time.time()

        if not query:
            return AgentResult.failed(error="query 不能为空")

        # 加载 MCP 和 A2A 工具（短连接模式）
        mcp_tools, mcp_toolkit = await self._load_mcp_tools()
        a2a_tools = await self._load_a2a_tools()
        all_tools = self.base_tools + mcp_tools + a2a_tools

        try:
            logger.info(f"📋 [{spec.name}] 开始执行: {query[:100]}...")

            # 发送 Agent 开始事件
            await event_bus.emit(
                self,
                AgentStartedEvent(
                    agent_id=spec.id,
                    agent_name=spec.name,
                    key=key,
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
                    _tools=all_tools,
                    _state=state,
                )

                # 校验 agent_class（防呆：手动创建 AgentSpec 但未设置 agent_class）
                if spec.agent_class is None:
                    raise ValueError(
                        f"Agent {spec.id} 的 agent_class 为 None。"
                        f"请使用 @agent 装饰器注册 Agent，或手动设置 AgentSpec.agent_class。"
                    )

                # 调用 Agent 的 run() 方法（每次执行创建新实例，避免单例共享）
                # 如果上下文超限，压缩后重试一次
                agent_timeout = spec.get_timeout_seconds()
                instance = spec.agent_class()
                try:
                    result = await asyncio.wait_for(
                        instance.run(ctx),
                        timeout=agent_timeout,
                    )
                except asyncio.TimeoutError:
                    error_msg = f"Agent 执行超时（{agent_timeout}秒）"
                    logger.error(f"⏰ [{spec.name}] {error_msg}")
                    await self._emit_failed_event(spec, key, start_time, error_msg, "TimeoutError")
                    return AgentResult.failed(error=error_msg, messages=ctx._messages)
                except ContextLengthExceededError:
                    logger.warning(f"⚠️ [{spec.name}] 上下文超限，压缩消息后重试")
                    # 压缩 state 中的 messages
                    compressed_state = await self._compress_state_messages(state)
                    # 重新构建 AgentContext
                    ctx = AgentContext(
                        namespace=compressed_state["namespace"],
                        session_id=compressed_state["session_id"],
                        query=query,
                        _spec=spec,
                        _knowledge_prompt=knowledge_prompt,
                        _experience_prompt=experience_context or "",
                        _llm=self.llm,
                        _tools=all_tools,
                        _state=compressed_state,
                    )
                    # 重试（带超时，不再捕获，如果还是超限则抛出）
                    instance = spec.agent_class()
                    result = await asyncio.wait_for(
                        instance.run(ctx),
                        timeout=agent_timeout,
                    )

                # 处理 None
                if result is None:
                    await self._emit_failed_event(spec, key, start_time, "run() 返回 None", "NoneReturnError")
                    return AgentResult.failed(error="run() 返回 None", messages=ctx._messages)

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
                            key=key,
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
                await self._emit_failed_event(spec, key, start_time, str(e), type(e).__name__)
                return AgentResult.system_error(error=str(e), messages=ctx._messages)

        finally:
            # 短连接模式：执行完关闭 MCP 连接
            if mcp_toolkit:
                try:
                    await mcp_toolkit.close()
                    logger.debug(f"🔌 [{spec.name}] MCP 连接已关闭")
                except Exception as e:
                    logger.warning(f"🔌 [{spec.name}] MCP 连接关闭失败: {e}")

    async def _emit_failed_event(
        self,
        spec: AgentSpec,
        key: SessionKey,
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
                key=key,
                error=error,
                error_type=error_type,
            ),
        )

    async def _compress_state_messages(self, state: dict) -> dict:
        """
        压缩 state 中的 messages

        当 Agent 执行因上下文超限失败时调用。
        使用 Compactor 压缩历史消息，返回更新后的 state。

        Args:
            state: 原始 state

        Returns:
            包含压缩后 messages 的新 state
        """
        messages = state.get("messages", [])
        if not messages:
            return state

        compactor = get_compactor()
        compressed_messages, result = await compactor.compact(messages)

        if result.success and result.removed_count > 0:
            logger.info(
                f"📦 消息压缩完成: 移除 {result.removed_count} 条, "
                f"保留 {result.kept_count} 条"
            )
            new_state = state.copy()
            new_state["messages"] = compressed_messages
            return new_state

        if not result.success:
            logger.warning(f"📦 消息压缩失败: {result.error}")

        return state
