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
from typing import Any

from langgraph.errors import GraphInterrupt
from langgraph.types import Command

from datapillar_oneagentic.a2a.tool import create_a2a_tools
from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.core.config import AgentConfig
from datapillar_oneagentic.core.context import AgentContext, DelegationSignal
from datapillar_oneagentic.exception import AgentError, AgentErrorCategory, AgentErrorClassifier
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind
from datapillar_oneagentic.core.types import AgentResult, SessionKey
from datapillar_oneagentic.events import (
    AgentCompletedEvent,
    AgentFailedEvent,
    AgentStartedEvent,
    EventBus,
)
from datapillar_oneagentic.mcp.tool import MCPToolkit
from datapillar_oneagentic.context.compaction import Compactor
from datapillar_oneagentic.providers.llm.llm import ResilientChatModel
from datapillar_oneagentic.exception import (
    LLMError,
    LLMErrorCategory,
    RecoveryAction,
    calculate_retry_delay,
)
from datapillar_oneagentic.tools.delegation import create_delegation_tools
from datapillar_oneagentic.todo.audit import audit_todo_updates
from datapillar_oneagentic.todo.session_todo import SessionTodoList
from datapillar_oneagentic.todo.tool import (
    build_todo_tool_message,
    create_todo_tools,
    extract_todo_updates,
)

logger = logging.getLogger(__name__)


class AgentExecutor:
    """
    Agent 执行器

    负责执行单个 Agent，构建 AgentContext，处理返回结果。
    """

    def __init__(
        self,
        spec: AgentSpec,
        *,
        agent_config: AgentConfig,
        event_bus: EventBus,
        compactor: Compactor,
        llm_provider,
        agent_name_map: dict[str, str] | None = None,
    ):
        """
        创建执行器

        参数：
        - spec: Agent 规格
        """
        self.spec = spec
        self._agent_config = agent_config
        self._event_bus = event_bus
        self._compactor = compactor

        # 业务工具（显式传入）
        self.business_tools = list(spec.tools or [])

        # 创建委派工具（使用正统实现）
        agent_name_map = agent_name_map or {}
        agent_names = {
            agent_id: agent_name_map.get(agent_id, agent_id)
            for agent_id in (spec.can_delegate_to or [])
        }
        self.delegation_tools = create_delegation_tools(
            can_delegate_to=spec.can_delegate_to or [],
            agent_names=agent_names,
        )

        # Todo 工具（团队级进度上报）
        self.todo_tools = create_todo_tools()

        # 基础工具（不含 MCP/A2A，这些在执行时动态加载）
        self.base_tools = self.business_tools + self.delegation_tools + self.todo_tools

        # 创建 LLM（团队级配置）
        self.llm: ResilientChatModel = llm_provider(temperature=spec.temperature)
        self._todo_audit_llm: ResilientChatModel = llm_provider(temperature=0.0)

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

    async def _maybe_append_todo_audit_report(
        self,
        *,
        state: dict,
        result_status: ExecutionStatus,
        failure_kind: FailureKind | None,
        deliverable: Any,
        error: str | None,
        messages: list,
        llm: ResilientChatModel,
    ) -> None:
        """没有上报时，追加 Todo 审计结果到消息中"""
        todo_data = state.get("todo")
        if not todo_data:
            return

        if extract_todo_updates(messages):
            return

        if result_status == ExecutionStatus.FAILED and failure_kind == FailureKind.SYSTEM:
            return

        try:
            todo = SessionTodoList.model_validate(todo_data)
        except Exception as exc:
            logger.warning(f"Todo 审计跳过（解析失败）: {exc}")
            return

        try:
            updates = await audit_todo_updates(
                todo=todo,
                agent_status=result_status,
                deliverable=deliverable,
                error=error,
                llm=llm,
            )
        except Exception as exc:
            logger.warning(f"Todo 审计失败: {exc}")
            return

        if updates:
            messages.append(build_todo_tool_message(updates))

    async def execute(
        self,
        *,
        query: str,
        state: dict,
        additional_tools: list[Any] | None = None,
    ) -> AgentResult | Command:
        """
        执行 Agent

        参数：
        - query: 用户输入
        - state: 共享状态（必须包含 namespace 和 session_id）
        - additional_tools: 额外工具（框架内部注入）

        返回：
        - AgentResult 或 Command（委派）

        异常：
        - LLMError / AgentError：失败直接抛出，供上层处理

        注意：
        - MCP 工具采用短连接模式：执行时连接，执行完关闭
        - Store 通过 LangGraph 的 get_store() 自动获取，无需手动传递
        """
        spec = self.spec
        key = SessionKey(namespace=state["namespace"], session_id=state["session_id"])
        llm_with_context = self.llm.with_event_context(agent_id=spec.id, key=key)
        todo_audit_llm = self._todo_audit_llm.with_event_context(agent_id=spec.id, key=key)
        start_time = time.time()

        if not query:
            raise AgentError(
                "query 不能为空",
                agent_id=spec.id,
                category=AgentErrorCategory.BUSINESS,
                action=RecoveryAction.FAIL_FAST,
                failure_kind=FailureKind.BUSINESS,
            )

        # 加载 MCP 和 A2A 工具（短连接模式）
        mcp_tools, mcp_toolkit = await self._load_mcp_tools()
        a2a_tools = await self._load_a2a_tools()
        extra_tools = additional_tools or []
        all_tools = self.base_tools + extra_tools + mcp_tools + a2a_tools

        try:
            logger.info(f"📋 [{spec.name}] 开始执行: {query[:100]}...")

            await self._event_bus.emit(
                self,
                AgentStartedEvent(
                    agent_id=spec.id,
                    agent_name=spec.name,
                    key=key,
                    query=query[:200],
                ),
            )

            if spec.agent_class is None:
                raise AgentError(
                    f"Agent {spec.id} 的 agent_class 为 None。"
                    "请使用 @agent 装饰器注册 Agent，或手动设置 AgentSpec.agent_class。",
                    agent_id=spec.id,
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )

            agent_timeout = spec.get_timeout_seconds(self._agent_config)
            retry_config = spec.get_retry_config(self._agent_config)
            max_retries = retry_config.max_retries
            retry_count = 0
            context_retry_used = False
            run_state = state

            while True:
                try:
                    ctx = AgentContext(
                        namespace=run_state["namespace"],
                        session_id=run_state["session_id"],
                        query=query,
                        _spec=spec,
                        _llm=llm_with_context,
                        _tools=all_tools,
                        _state=run_state,
                        _agent_config=self._agent_config,
                        _event_bus=self._event_bus,
                    )

                    instance = spec.agent_class()
                    result = await asyncio.wait_for(
                        instance.run(ctx),
                        timeout=agent_timeout,
                    )

                except DelegationSignal as signal:
                    logger.info(f"🔄 [{spec.name}] 委派给 {signal.command.goto}")
                    return signal.command

                except LLMError as error:
                    if error.agent_id is None:
                        error.attach_agent_id(spec.id)

                    if error.category == LLMErrorCategory.CONTEXT and not context_retry_used:
                        logger.warning(f"⚠️ [{spec.name}] 上下文超限，压缩消息后重试")
                        run_state = await self._compress_state_messages(run_state)
                        context_retry_used = True
                        continue

                    if error.action == RecoveryAction.RETRY and retry_count < max_retries:
                        delay = calculate_retry_delay(retry_config, retry_count)
                        retry_count += 1
                        logger.warning(
                            f"🔁 [{spec.name}] LLM 异常重试 "
                            f"{retry_count}/{max_retries}，{delay:.2f}s 后重试: {error}"
                        )
                        await asyncio.sleep(delay)
                        continue

                    await self._emit_failed_event(
                        spec,
                        key,
                        start_time,
                        str(error),
                        f"LLMError:{error.category.value}",
                    )
                    raise

                except AgentError as error:
                    if error.action == RecoveryAction.RETRY and retry_count < max_retries:
                        delay = calculate_retry_delay(retry_config, retry_count)
                        retry_count += 1
                        logger.warning(
                            f"🔁 [{spec.name}] Agent 异常重试 "
                            f"{retry_count}/{max_retries}，{delay:.2f}s 后重试: {error}"
                        )
                        await asyncio.sleep(delay)
                        continue

                    await self._emit_failed_event(
                        spec,
                        key,
                        start_time,
                        str(error),
                        f"AgentError:{error.category.value}",
                    )
                    raise

                except GraphInterrupt:
                    raise
                except Exception as exc:
                    agent_error = AgentErrorClassifier.from_exception(agent_id=spec.id, error=exc)
                    if agent_error.action == RecoveryAction.RETRY and retry_count < max_retries:
                        delay = calculate_retry_delay(retry_config, retry_count)
                        retry_count += 1
                        logger.warning(
                            f"🔁 [{spec.name}] 系统异常重试 "
                            f"{retry_count}/{max_retries}，{delay:.2f}s 后重试: {agent_error}"
                        )
                        await asyncio.sleep(delay)
                        continue

                    await self._emit_failed_event(
                        spec,
                        key,
                        start_time,
                        str(agent_error),
                        f"AgentError:{agent_error.category.value}",
                    )
                    raise agent_error

                if result is None:
                    agent_error = AgentError(
                        "run() 返回 None",
                        agent_id=spec.id,
                        category=AgentErrorCategory.PROTOCOL,
                        action=RecoveryAction.FAIL_FAST,
                        failure_kind=FailureKind.SYSTEM,
                    )
                    await self._emit_failed_event(
                        spec,
                        key,
                        start_time,
                        str(agent_error),
                        "AgentError:protocol",
                    )
                    raise agent_error

                if isinstance(result, AgentResult):
                    if result.status == ExecutionStatus.FAILED:
                        failure_kind = result.failure_kind or FailureKind.BUSINESS
                        agent_error = AgentErrorClassifier.from_failure(
                            agent_id=spec.id,
                            error=result.error or "Agent 执行失败",
                            failure_kind=failure_kind,
                        )
                        await self._emit_failed_event(
                            spec,
                            key,
                            start_time,
                            str(agent_error),
                            f"AgentError:{agent_error.category.value}",
                        )
                        raise agent_error

                    if result.status != ExecutionStatus.COMPLETED:
                        agent_error = AgentError(
                            f"Agent {spec.id} 返回未知状态: {result.status}",
                            agent_id=spec.id,
                            category=AgentErrorCategory.PROTOCOL,
                            action=RecoveryAction.FAIL_FAST,
                            failure_kind=FailureKind.SYSTEM,
                        )
                        await self._emit_failed_event(
                            spec,
                            key,
                            start_time,
                            str(agent_error),
                            "AgentError:protocol",
                        )
                        raise agent_error

                    deliverable = result.deliverable
                    result_messages = result.messages or ctx._messages
                else:
                    deliverable = result
                    result_messages = ctx._messages

                if isinstance(deliverable, spec.deliverable_schema):
                    logger.info(f"✅ [{spec.name}] 完成")

                    duration_ms = (time.time() - start_time) * 1000
                    await self._event_bus.emit(
                        self,
                        AgentCompletedEvent(
                            agent_id=spec.id,
                            agent_name=spec.name,
                            key=key,
                            result="completed",
                            duration_ms=duration_ms,
                        ),
                    )

                    await self._maybe_append_todo_audit_report(
                        state=state,
                        result_status=ExecutionStatus.COMPLETED,
                        failure_kind=None,
                        deliverable=deliverable,
                        error=None,
                        messages=result_messages,
                        llm=todo_audit_llm,
                    )

                    return AgentResult.completed(
                        deliverable=deliverable,
                        deliverable_type=spec.id,
                        messages=result_messages,
                    )

                agent_error = AgentError(
                    f"Agent {spec.id} 的 run() 返回类型错误: "
                    f"期望 {spec.deliverable_schema.__name__}, "
                    f"实际 {type(deliverable).__name__}",
                    agent_id=spec.id,
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )
                await self._emit_failed_event(
                    spec,
                    key,
                    start_time,
                    str(agent_error),
                    "AgentError:protocol",
                )
                raise agent_error

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
        await self._event_bus.emit(
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

        compressed_messages, result = await self._compactor.compact(messages)

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
