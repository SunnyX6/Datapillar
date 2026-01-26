"""
Agent executor.

Executes a single agent:
1. Prepare AgentContext
2. Call Agent.run()
3. Handle return value
4. Emit execution events
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
    build_todo_message,
    create_todo_tools,
    extract_todo_updates,
)
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.messages import Messages

logger = logging.getLogger(__name__)


class AgentExecutor:
    """
    Agent executor.

    Executes a single agent, builds AgentContext, and handles results.
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
        Create an executor.

        Args:
            spec: agent specification
        """
        self.spec = spec
        self._agent_config = agent_config
        self._event_bus = event_bus
        self._compactor = compactor

        # Business tools (explicitly provided).
        self.business_tools = list(spec.tools or [])

        # Create delegation tools (standard implementation).
        agent_name_map = agent_name_map or {}
        agent_names = {
            agent_id: agent_name_map.get(agent_id, agent_id)
            for agent_id in (spec.can_delegate_to or [])
        }
        self.delegation_tools = create_delegation_tools(
            can_delegate_to=spec.can_delegate_to or [],
            agent_names=agent_names,
        )

        # Todo tools (team-level progress reporting).
        self.todo_tools = create_todo_tools()

        # Base tools (MCP/A2A loaded dynamically at runtime).
        self.base_tools = self.business_tools + self.delegation_tools + self.todo_tools

        # Create LLM instances (team-level configuration).
        self.llm: ResilientChatModel = llm_provider(temperature=spec.temperature)
        self._todo_audit_llm: ResilientChatModel = llm_provider(temperature=0.0)

        logger.info(
            f"Executor created: {spec.name} ({spec.id}), "
            f"tools: {len(self.business_tools)}, delegates: {len(self.delegation_tools)}, "
            f"MCP servers: {len(spec.mcp_servers)}, A2A agents: {len(spec.a2a_agents)}"
        )

    async def _load_mcp_tools(self) -> tuple[list, MCPToolkit | None]:
        """Load MCP tools (short-lived connection)."""
        spec = self.spec
        if not spec.mcp_servers:
            return [], None

        try:
            toolkit = MCPToolkit(spec.mcp_servers)
            await toolkit.connect()
            tools = toolkit.get_tools()
            logger.info(f"[{spec.name}] MCP tools loaded: {len(tools)}")
            return tools, toolkit
        except Exception as e:
            logger.error(f"[{spec.name}] MCP tool load failed: {e}")
            return [], None

    async def _load_a2a_tools(self) -> list:
        """Load A2A tools."""
        spec = self.spec
        if not spec.a2a_agents:
            return []

        try:
            tools = await create_a2a_tools(spec.a2a_agents)
            logger.info(f"[{spec.name}] A2A tools loaded: {len(tools)}")
            return tools
        except Exception as e:
            logger.error(f"[{spec.name}] A2A tool load failed: {e}")
            return []

    async def _append_todo_audit(
        self,
        *,
        state: dict,
        result_status: ExecutionStatus,
        failure_kind: FailureKind | None,
        deliverable: Any,
        error: str | None,
        messages: Messages,
        llm: ResilientChatModel,
    ) -> None:
        """Append todo audit results to messages when no updates were reported."""
        sb = StateBuilder(state)
        todo_data = sb.todo.snapshot().todo
        if not todo_data:
            return

        if extract_todo_updates(messages):
            return

        if result_status == ExecutionStatus.FAILED and failure_kind == FailureKind.SYSTEM:
            return

        try:
            todo = SessionTodoList.model_validate(todo_data)
        except Exception as exc:
            logger.warning(f"Todo audit skipped (parse failed): {exc}")
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
            logger.warning(f"Todo audit failed: {exc}")
            return

        if updates:
            messages.append(build_todo_message(updates))

    async def execute(
        self,
        *,
        query: str,
        state: dict,
        additional_tools: list[Any] | None = None,
    ) -> AgentResult | Command:
        """
        Execute an agent.

        Args:
            query: user input
            state: shared state (must include namespace and session_id)
            additional_tools: additional tools injected by the framework

        Returns:
            AgentResult or Command (delegation).

        Raises:
            LLMError / AgentError: raised on failure for upstream handling.

        Notes:
            - MCP tools use short-lived connections (connect per run, close after).
            - Store is retrieved via LangGraph get_store(), no manual injection needed.
        """
        spec = self.spec
        sb = StateBuilder(state)
        key = sb.key()
        llm_with_context = self.llm.with_event_context(agent_id=spec.id, key=key)
        todo_audit_llm = self._todo_audit_llm.with_event_context(agent_id=spec.id, key=key)
        start_time = time.time()

        if not query:
            raise AgentError(
                "query must not be empty",
                agent_id=spec.id,
                category=AgentErrorCategory.BUSINESS,
                action=RecoveryAction.FAIL_FAST,
                failure_kind=FailureKind.BUSINESS,
            )

        # Load MCP and A2A tools (short-lived connections).
        mcp_tools, mcp_toolkit = await self._load_mcp_tools()
        a2a_tools = await self._load_a2a_tools()
        extra_tools = additional_tools or []
        all_tools = self.base_tools + extra_tools + mcp_tools + a2a_tools

        try:
            logger.info(f"[{spec.name}] Execution started: {query[:100]}...")

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
                    f"Agent {spec.id} has agent_class=None. "
                    "Register the agent with @agent or set AgentSpec.agent_class explicitly.",
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
                    run_sb = StateBuilder(run_state)
                    ctx = AgentContext(
                        namespace=run_sb.namespace,
                        session_id=run_sb.session_id,
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
                    logger.info(f"[{spec.name}] Delegated to {signal.command.goto}")
                    return signal.command

                except LLMError as error:
                    if error.agent_id is None:
                        error.attach_agent_id(spec.id)

                    if error.category == LLMErrorCategory.CONTEXT and not context_retry_used:
                        logger.warning(
                            f"[{spec.name}] Context limit exceeded; compressing messages and retrying"
                        )
                        run_state = await self._compress_state_messages(run_state)
                        context_retry_used = True
                        continue

                    if error.action == RecoveryAction.RETRY and retry_count < max_retries:
                        delay = calculate_retry_delay(retry_config, retry_count)
                        retry_count += 1
                        logger.warning(
                            f"[{spec.name}] LLM retry {retry_count}/{max_retries} "
                            f"after {delay:.2f}s: {error}"
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
                            f"[{spec.name}] Agent retry {retry_count}/{max_retries} "
                            f"after {delay:.2f}s: {error}"
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
                            f"[{spec.name}] System retry {retry_count}/{max_retries} "
                            f"after {delay:.2f}s: {agent_error}"
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
                        "run() returned None",
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
                            error=result.error or "Agent execution failed",
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
                            f"Agent {spec.id} returned an unknown status: {result.status}",
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
                    logger.info(f"[{spec.name}] Completed")

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

                    await self._append_todo_audit(
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
                    f"Agent {spec.id} run() returned the wrong type: "
                    f"expected {spec.deliverable_schema.__name__}, "
                    f"got {type(deliverable).__name__}",
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
            # Short-lived mode: close MCP connection after execution.
            if mcp_toolkit:
                try:
                    await mcp_toolkit.close()
                    logger.debug(f"[{spec.name}] MCP connection closed")
                except Exception as e:
                    logger.warning(f"[{spec.name}] MCP connection close failed: {e}")

    async def _emit_failed_event(
        self,
        spec: AgentSpec,
        key: SessionKey,
        start_time: float,
        error: str,
        error_type: str,
    ) -> None:
        """Emit an AgentFailed event."""
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
        Compress messages in state.

        Called when an agent fails due to context limits.
        Uses Compactor to shrink message history and returns updated state.

        Args:
            state: original state

        Returns:
            Updated state with compressed messages.
        """
        sb = StateBuilder(state)
        messages = sb.memory.snapshot()
        if not messages:
            return state
        if self._compactor is None:
            return state

        compressed_messages, result = await self._compactor.compact(messages)

        if result.success and result.removed_count > 0:
            if result.summary:
                sb.compression.set_runtime_compression(result.summary)
            # Runtime-only: shorten next LLM call without writing to checkpoint.
            sb.memory.replace_runtime_only(compressed_messages)
            return state

        if not result.success:
            logger.warning(f"Message compaction failed: {result.error}")

        return state
