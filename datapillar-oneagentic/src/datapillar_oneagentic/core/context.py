# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Agent execution context.

AgentContext is the interface exposed to business agents:
- Read-only info: namespace, query, session_id
- Methods: messages, invoke_tools, get_structured_output, interrupt
- Dependency access: get_deliverable

Design principles:
- Business code only uses public methods/properties
- Internal objects are private to prevent escalation
- Memory/LLM/tools are managed by the framework
- Delegation is handled internally
- Store access is encapsulated with a simple API
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from langgraph.prebuilt import ToolNode
from langgraph.types import Command, interrupt

from datapillar_oneagentic.context import ContextBuilder, ContextComposer
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.events import (
    EventBus,
    LLMThinkingEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
)
from datapillar_oneagentic.providers.llm.llm import extract_thinking
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.messages.adapters.langchain import from_langchain, to_langchain
from datapillar_oneagentic.utils.structured_output import parse_structured_output

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec
    from datapillar_oneagentic.core.config import AgentConfig

logger = logging.getLogger(__name__)


class AbortInterrupt(Exception):
    """User aborted an interrupt (control-flow exception)."""


def _is_abort_payload(value: Any) -> bool:
    if not isinstance(value, dict):
        return False
    return value.get("__abort__") is True


class DelegationSignal(Exception):
    """
    Delegation signal (framework internal).

    Raised when an agent calls a delegation tool; handled by the executor.
    """

    def __init__(self, command: Command):
        self.command = command
        super().__init__(f"Delegation to {command.goto}")


@dataclass
class AgentContext:
    """
    Agent execution context.

    Business agents interact with the framework through this context.

    Public attributes (read-only):
    - namespace: namespace
    - session_id: session ID
    - query: user input

    Public methods:
    - messages(base=None): create a Messages sequence
    - invoke_tools(messages): run tool call loop
    - get_structured_output(messages): parse structured output
    - interrupt(payload): pause and wait for user response
    - get_deliverable(agent_id): fetch another agent's deliverable

    Example:
    ```python
    async def run(self, ctx: AgentContext) -> AnalysisOutput:
        # Fetch upstream deliverable by agent_id
        upstream_data = await ctx.get_deliverable(agent_id="data_extractor")

        # 1. Build messages
        messages = ctx.messages().system(self.SYSTEM_PROMPT)

        # 2. Tool-call loop (delegation handled by framework)
        messages = await ctx.invoke_tools(messages)

        # 3. Get structured output
        output = await ctx.get_structured_output(messages)

        # 4. Business logic
        if output.confidence < 0.7:
            user_reply = ctx.interrupt("Requirements are not clear enough")
            # Continue after enriching context with user_reply

        return output
    ```
    """

    # === Public read-only attributes ===
    namespace: str
    """Namespace."""

    session_id: str
    """Session ID."""

    query: str
    """User input."""

    # === Framework internals (private) ===
    _spec: AgentSpec = field(default=None, repr=False)
    """Agent spec (internal)."""

    _llm: Any = field(default=None, repr=False)
    """LLM instance (internal)."""

    _tools: list[Any] = field(default_factory=list, repr=False)
    """Tool list (internal)."""

    _state: dict = field(default_factory=dict, repr=False)
    """Shared state (internal)."""

    _delegation_command: Command | None = field(default=None, repr=False)
    """Delegation command (internal)."""

    _messages: Messages = field(default_factory=Messages, repr=False)
    _agent_config: AgentConfig | None = field(default=None, repr=False)
    _event_bus: EventBus | None = field(default=None, repr=False)
    """Message history (internal)."""

    # === Public methods ===

    def messages(self, base: Messages | None = None) -> Messages:
        """
        Create a Messages sequence using the framework protocol.

        Args:
            base: optional Messages instance to seed the sequence

        Returns:
            Messages instance for chaining.
        """
        if base is None:
            messages = Messages()
        elif isinstance(base, Messages):
            messages = Messages(base)
        else:
            raise TypeError("messages only accepts Messages or None")
        self._messages = messages
        return messages

    def _compose_messages(self, messages: Messages) -> Messages:
        if not isinstance(messages, Messages):
            raise TypeError("messages must be Messages")
        prefix, rest = self._split_system(messages)
        contexts = ContextBuilder.extract_context_blocks(self._state)
        checkpoint_messages = StateBuilder(self._state).memory.snapshot()
        if self._has_user(messages):
            checkpoint_messages = self._trim_tail_user(checkpoint_messages, messages)

        llm_messages = Messages()
        llm_messages.extend(prefix)
        if contexts:
            ContextComposer._append_context_messages(llm_messages, contexts)
        if checkpoint_messages:
            llm_messages.extend(checkpoint_messages)
        if rest:
            llm_messages.extend(rest)
        return llm_messages

    @staticmethod
    def _split_system(messages: Messages) -> tuple[Messages, Messages]:
        prefix = Messages()
        rest = Messages()
        found_non_system = False
        for msg in messages:
            if not found_non_system and msg.role == "system":
                prefix.append(msg)
                continue
            found_non_system = True
            rest.append(msg)
        return prefix, rest

    @staticmethod
    def _has_user(messages: Messages) -> bool:
        return any(msg.role == "user" for msg in messages)

    @staticmethod
    def _trim_tail_user(checkpoint_messages: Messages, base_messages: Messages) -> Messages:
        if not checkpoint_messages:
            return checkpoint_messages
        last = checkpoint_messages[-1]
        if last.role != "user":
            return checkpoint_messages
        base_users = [
            str(msg.content).strip()
            for msg in base_messages
            if msg.role == "user" and str(msg.content).strip()
        ]
        if not base_users:
            return checkpoint_messages
        last_text = str(last.content).strip()
        if last_text and last_text in base_users:
            return Messages(checkpoint_messages[:-1])
        return checkpoint_messages

    def _has_tool(self, tool_name: str) -> bool:
        for tool in self._tools or []:
            name = getattr(tool, "name", None)
            if not name and callable(tool):
                name = getattr(tool, "__name__", "")
            if name == tool_name:
                return True
        return False

    @staticmethod
    def _tool_name(tool: Any) -> str:
        name = getattr(tool, "name", None)
        if not name and callable(tool):
            name = getattr(tool, "__name__", "")
        return name or ""

    def _collect_bound_namespaces(self) -> dict[str, list[str]]:
        bound_map: dict[str, list[str]] = {}
        for tool in self._tools or []:
            name = self._tool_name(tool)
            if not name:
                continue
            bound = getattr(tool, "bound_namespaces", None)
            if bound:
                bound_map[name] = list(bound)
        return bound_map

    @staticmethod
    def _inject_tool_call_namespaces(
        tool_calls: list[Any],
        *,
        bound_namespaces: list[str] | None,
    ) -> None:
        if not tool_calls:
            return
        missing_bound = not bound_namespaces
        for tc in tool_calls:
            name = getattr(tc, "name", None)
            if not name and isinstance(tc, dict):
                name = tc.get("name")
            if name != "knowledge_retrieve":
                continue
            if missing_bound:
                raise ValueError("knowledge_retrieve namespaces missing and no bound namespaces provided")
            args = getattr(tc, "args", None)
            if args is None and isinstance(tc, dict):
                args = tc.get("args")
            if not isinstance(args, dict):
                args = {}
            if not args.get("namespaces"):
                args["namespaces"] = list(bound_namespaces)
                if isinstance(tc, dict):
                    tc["args"] = args
                else:
                    try:
                        setattr(tc, "args", args)
                    except Exception:
                        pass

    async def invoke_tools(self, messages: Messages) -> Messages:
        """
        Tool invocation loop.

        Runs LLM calls and tool calls until the model stops calling tools.
        If a delegation tool is called, DelegationSignal is raised for the framework.

        Notes:
        - With tools, use bind_tools for tool loop without forcing structured output
        - Without tools, use structured output to avoid extra get_structured_output call
        - Tool path still needs get_structured_output for parsing final output

        Args:
            messages: message object from ctx.messages()

        Returns:
            Updated messages.

        Raises:
            DelegationSignal: raised when delegation tools are called.
        """
        schema = self._spec.deliverable_schema

        if not self._tools:
            # No tools: call LLM with structured output directly.
            llm_structured = self._llm.with_structured_output(schema, method="function_calling")
            llm_messages = self._compose_messages(messages)
            response = await llm_structured.ainvoke(llm_messages)
            # Serialize Pydantic output as JSON string and wrap into assistant message.
            if hasattr(response, "model_dump_json"):
                content = response.model_dump_json()
            else:
                import json
                content = json.dumps(response) if isinstance(response, dict) else str(response)
            assistant_message = Message.assistant(content)
            llm_messages.append(assistant_message)
            messages.append(assistant_message)
            self._messages = messages
            return messages

        # Create ToolNode.
        tool_node = ToolNode(self._tools)

        # Bind tools for tool invocation loop.
        llm_with_tools = self._llm.bind_tools(self._tools)

        llm_messages = self._compose_messages(messages)
        max_steps = self._spec.get_max_steps(self._agent_config)
        key = SessionKey(namespace=self.namespace, session_id=self.session_id)
        bound_map = self._collect_bound_namespaces()
        bound_knowledge = bound_map.get("knowledge_retrieve")
        for _iteration in range(1, max_steps + 1):
            # LLM call.
            response = await llm_with_tools.ainvoke(llm_messages)

            # Extract and emit thinking content if present.
            thinking_content = self._extract_thinking(response)
            if thinking_content:
                await self._emit_event(
                    LLMThinkingEvent(
                        agent_id=self._spec.id,
                        key=key,
                        thinking_content=thinking_content,
                    )
                )

            if not response.tool_calls:
                # No tool calls, end loop.
                llm_messages.append(response)
                messages.append(response)
                break

            self._inject_tool_call_namespaces(
                response.tool_calls,
                bound_namespaces=bound_knowledge,
            )

            llm_messages.append(response)
            messages.append(response)

            # Track tool call info for completion/failure events.
            tool_calls_info = []
            for tc in response.tool_calls:
                tool_name = getattr(tc, "name", None)
                tool_args = getattr(tc, "args", {})
                tool_call_id = getattr(tc, "id", "")

                if not tool_name:
                    continue

                logger.info(f"[{self._spec.name}] Tool called: {tool_name}")
                tool_calls_info.append({
                    "name": tool_name,
                    "args": tool_args if isinstance(tool_args, dict) else {},
                    "id": tool_call_id or "",
                })
                await self._emit_event(
                    ToolCalledEvent(
                        agent_id=self._spec.id,
                        key=key,
                        tool_name=tool_name,
                        tool_call_id=tool_call_id or "",
                        tool_input=tool_args if isinstance(tool_args, dict) else {},
                    )
                )

            # Execute tools with timeout control.
            import time
            tool_start_time = time.time()
            current_state = dict(self._state)
            current_state["messages"] = to_langchain(messages)
            tool_error = None
            tool_timeout = self._spec.get_tool_timeout(self._agent_config)
            try:
                result = await asyncio.wait_for(
                    tool_node.ainvoke(current_state),
                    timeout=tool_timeout,
                )
            except asyncio.TimeoutError:
                tool_error = f"Tool call timed out ({tool_timeout}s)"
                logger.error(f"[{self._spec.name}] Tool error: {tool_error}")
                for tc_info in tool_calls_info:
                    await self._emit_event(
                        ToolFailedEvent(
                            agent_id=self._spec.id,
                            key=key,
                            tool_name=tc_info["name"],
                            tool_call_id=tc_info["id"],
                            error=tool_error,
                        )
                    )
                raise TimeoutError(tool_error)
            except Exception as e:
                tool_error = str(e)
                # Emit failure events for all tool calls.
                for tc_info in tool_calls_info:
                    await self._emit_event(
                        ToolFailedEvent(
                            agent_id=self._spec.id,
                            key=key,
                            tool_name=tc_info["name"],
                            tool_call_id=tc_info["id"],
                            error=tool_error,
                        )
                    )
                raise
            tool_duration_ms = (time.time() - tool_start_time) * 1000

            # Parse tool results: separate Command and messages.
            delegation_command = None
            new_messages = Messages()

            if isinstance(result, dict):
                raw_messages = result.get("messages", [])
                if raw_messages:
                    new_messages = Messages(from_langchain(raw_messages))
            elif isinstance(result, list):
                for item in result:
                    if isinstance(item, Command):
                        # Only keep the first Command (multiple delegations are invalid).
                        if delegation_command is None:
                            delegation_command = item
                        else:
                            logger.warning(f"[{self._spec.name}] Extra delegation command ignored")
                    else:
                        if isinstance(item, Message):
                            new_messages.append(item)
                        else:
                            new_messages.append(from_langchain(item))

            # Handle delegation command.
            if delegation_command is not None:
                self._delegation_command = delegation_command
                logger.info(f"[{self._spec.name}] Delegated to {self._delegation_command.goto}")
                self._messages = messages
                # Raise delegation signal for the framework.
                raise DelegationSignal(self._delegation_command)

            # Emit tool completion events (extract tool outputs from messages).
            tool_outputs = {}
            for msg in new_messages:
                if msg.role == "tool" and msg.tool_call_id:
                    tool_outputs[msg.tool_call_id] = msg.content

            for tc_info in tool_calls_info:
                tool_output = tool_outputs.get(tc_info["id"], "")
                await self._emit_event(
                    ToolCompletedEvent(
                        agent_id=self._spec.id,
                        key=key,
                        tool_name=tc_info["name"],
                        tool_call_id=tc_info["id"],
                        tool_output=tool_output,
                        duration_ms=tool_duration_ms / len(tool_calls_info) if tool_calls_info else 0,
                    )
                )

            messages.extend(new_messages)
            llm_messages.extend(new_messages)

        self._messages = messages
        return messages

    async def get_structured_output(self, messages: Messages) -> Any:
        """
        Get structured output.

        Args:
            messages: message object returned by invoke_tools()

        Returns:
            deliverable_schema instance.
        """
        schema = self._spec.deliverable_schema
        # Direct structured output call to avoid parsing interference from extra messages.
        llm_structured = self._llm.with_structured_output(
            schema,
            method="function_calling",
            include_raw=True,
        )
        llm_messages = self._compose_messages(messages)
        result = await llm_structured.ainvoke(llm_messages)
        return parse_structured_output(result, schema, strict=False)

    async def _emit_event(self, event: Any) -> None:
        """Safely emit an event (event_bus may be None)."""
        if self._event_bus is None:
            return
        await self._event_bus.emit(self, event)

    def _extract_thinking(self, response: Message) -> str | None:
        """
        Extract thinking content from an LLM response.

        Supported formats:
        - GLM: additional_kwargs.reasoning_content
        - Claude: thinking blocks in content
        - DeepSeek: additional_kwargs.reasoning_content
        """
        if not isinstance(response, Message):
            return None
        return extract_thinking(response)

    def interrupt(self, payload: Any | None = None) -> Any:
        """
        Interrupt and wait for a user reply.

        payload is optional and should be serializable.
        Returns the user response and appends it to context messages.
        """
        resume_value = interrupt(payload)
        if _is_abort_payload(resume_value):
            raise AbortInterrupt("Aborted by user")
        self._append_user_reply(resume_value)
        return resume_value

    def _append_user_reply(self, resume_value: Any) -> None:
        """Append user reply as a user message (normalized structure)."""
        sb = StateBuilder(self._state)
        sb.append_reply_state(resume_value)
        # After resuming, subsequent calls should use the latest checkpoint memory.
        self._messages = sb.memory.snapshot()

    async def get_deliverable(self, agent_id: str) -> Any | None:
        """
        Get another agent's deliverable.

        Fetch deliverables by agent_id, typically for upstream dependencies.

        Args:
            agent_id: upstream agent ID

        Returns:
            Deliverable content (dict) or None if not found.

        Example:
        ```python
        async def run(self, ctx: AgentContext) -> ReportOutput:
            # Fetch analysis output
            analysis = await ctx.get_deliverable(agent_id="analyst")
            if not analysis:
                user_reply = ctx.interrupt("Missing analysis data")
                # Continue after providing data based on user_reply

            # Build report from analysis
            ...
        ```
        """
        from langgraph.config import get_store

        store = get_store()
        if not store:
            logger.warning("Store not configured; deliverable unavailable")
            return None

        store_namespace = ("deliverables", self.namespace, self.session_id)

        try:
            item = await store.aget(store_namespace, agent_id)
            if item:
                return item.value
            return None
        except Exception as e:
            logger.error(f"Failed to fetch deliverable: {e}")
            return None
